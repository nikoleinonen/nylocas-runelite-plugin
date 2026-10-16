package com.nylocas;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.util.AsyncBufferedImage;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
final class EventBoardClient
{
	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final ClientThread clientThread;
	private final ItemManager itemManager;

	private static final int MAX_IMAGE_BYTES = 1 << 20;

	private final AtomicInteger generation = new AtomicInteger();
	private final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();
	private final List<Call> imageCalls = new ArrayList<>();
	private Call fetchCall;
	private volatile boolean running;
	private volatile List<EventTile> tiles = List.of();
	private volatile List<EventPlayer> players = List.of();
	private volatile String eventName = "";
	private volatile String eventPassword = "";
	private volatile boolean eventLoaded;
	private volatile boolean eventStarted;

	EventBoardClient(OkHttpClient okHttpClient, Gson gson, ClientThread clientThread, ItemManager itemManager)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
	}

	void start()
	{
		running = true;
	}

	void stop()
	{
		running = false;
		generation.incrementAndGet();
		if (fetchCall != null)
		{
			fetchCall.cancel();
			fetchCall = null;
		}
		cancelImages();
		imageCache.clear();
		tiles = List.of();
		players = List.of();
		eventName = "";
		eventPassword = "";
		eventLoaded = false;
		eventStarted = false;
	}

	List<EventTile> tiles()
	{
		return tiles;
	}

	List<EventPlayer> players()
	{
		return players;
	}

	String eventName()
	{
		return eventName;
	}

	String eventPassword()
	{
		return eventPassword;
	}

	boolean eventLoaded()
	{
		return eventLoaded;
	}

	boolean eventStarted()
	{
		return eventStarted;
	}

	String itemName(int id)
	{
		String name = itemManager.getItemComposition(id).getName();
		if (name == null || name.isEmpty() || "null".equals(name))
		{
			return null;
		}
		return name;
	}

	boolean fetch(String key, EventPanel panel, boolean connect)
	{
		if (!connect)
		{
			panel.showStatus("Turn on Connect to nylocas.com in the plugin settings");
			return false;
		}
		int ticket = generation.incrementAndGet();
		cancelImages();
		if (fetchCall != null)
		{
			fetchCall.cancel();
		}
		Request request = new Request.Builder()
			.url(NylocasPlugin.API_BASE + "/plugin/event")
			.header("Authorization", "Bearer " + key)
			.header("Accept", "application/json")
			.build();
		fetchCall = okHttpClient.newCall(request);
		fetchCall.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException error)
			{
				if (call.isCanceled() || !running)
				{
					return;
				}
				log.debug("Fetch event failed", error);
				SwingUtilities.invokeLater(() -> panel.showStatus("Could not fetch event"));
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					if (call.isCanceled() || !running || ticket != generation.get())
					{
						return;
					}
					String body = response.body() == null ? "" : response.body().string();
					if (!running)
					{
						return;
					}
					if (!response.isSuccessful())
					{
						String message = errorMessage(body);
						SwingUtilities.invokeLater(() -> panel.showStatus(message));
						return;
					}
					EventBoard board = gson.fromJson(body, EventBoard.class);
					List<EventTile> next = new ArrayList<>();
					if (board != null && board.tiles != null)
					{
						for (EventTile tile : board.tiles)
						{
							if (tile != null)
							{
								next.add(tile);
							}
						}
					}
					if (!running || ticket != generation.get())
					{
						return;
					}
					// The panel keeps the fetched tiles. Proof reservations stay on this copy until the next refresh.
					tiles = NylocasPlugin.copyTiles(next);
					players = copyPlayers(board);
					eventName = board == null || board.eventName == null ? "" : board.eventName;
					eventPassword = passwordOf(board);
					eventStarted = board != null && board.eventStarted;
					eventLoaded = true;
					if (!running)
					{
						tiles = List.of();
						players = List.of();
						eventName = "";
						eventPassword = "";
						eventLoaded = false;
						eventStarted = false;
						return;
					}
					if (ticket != generation.get())
					{
						return;
					}
					EventBoard loaded = board;
					loadPictures(next, ticket, imagesByUrl -> showLoaded(panel, loaded, next, imagesByUrl, ticket));
				}
			}
		});
		return true;
	}

	private void loadPictures(List<EventTile> boardTiles, int ticket, Consumer<Map<String, BufferedImage>> done)
	{
		if (!running || ticket != generation.get())
		{
			return;
		}
		Map<String, BufferedImage> images = new ConcurrentHashMap<>();
		Set<String> urls = new LinkedHashSet<>();
		for (EventTile tile : boardTiles)
		{
			String primary = TileIcons.imageUrl(tile);
			if (TileIcons.allowed(primary))
			{
				urls.add(primary);
			}
			String catalog = TileIcons.catalogIconUrl(tile);
			if (catalog != null && !catalog.equals(primary) && TileIcons.allowed(catalog))
			{
				urls.add(catalog);
			}
		}
		List<String> missing = new ArrayList<>();
		for (String url : urls)
		{
			BufferedImage cached = imageCache.get(url);
			if (cached != null)
			{
				images.put(url, cached);
			}
			else
			{
				missing.add(url);
			}
		}
		if (missing.isEmpty())
		{
			done.accept(images);
			return;
		}
		List<CompletableFuture<Void>> jobs = new ArrayList<>();
		for (String url : missing)
		{
			if (!running || ticket != generation.get())
			{
				break;
			}
			CompletableFuture<Void> job = new CompletableFuture<>();
			jobs.add(job);
			Request request = new Request.Builder()
				.url(url)
				.header("Accept", "image/*")
				.build();
			Call call = okHttpClient.newCall(request);
			synchronized (imageCalls)
			{
				imageCalls.add(call);
			}
			call.enqueue(new Callback()
			{
				@Override
				public void onFailure(Call call, IOException error)
				{
					if (!call.isCanceled())
					{
						log.debug("Tile image {}", url, error);
					}
					finishImage(call, job);
				}

				@Override
				public void onResponse(Call call, Response response)
				{
					try (Response ignored = response)
					{
						BufferedImage image = readImage(response);
						if (image != null)
						{
							if (imageCache.size() >= 128)
							{
								imageCache.clear();
							}
							imageCache.put(url, image);
							images.put(url, image);
						}
					}
					catch (IOException ex)
					{
						log.debug("Tile image {}", url, ex);
					}
					finally
					{
						finishImage(call, job);
					}
				}
			});
		}
		CompletableFuture.allOf(jobs.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) ->
		{
			if (!running || ticket != generation.get())
			{
				return;
			}
			done.accept(images);
		});
	}

	private void showLoaded(EventPanel panel, EventBoard loaded, List<EventTile> boardTiles, Map<String, BufferedImage> imagesByUrl, int ticket)
	{
		if (!running || ticket != generation.get())
		{
			return;
		}
		clientThread.invoke(() ->
		{
			if (!running || ticket != generation.get())
			{
				return;
			}
			Map<Integer, String> names = new HashMap<>();
			Map<Integer, AsyncBufferedImage> images = new HashMap<>();
			Map<Long, TileIcons.Picture> pictures = picturesFor(boardTiles, imagesByUrl);
			for (EventTile tile : boardTiles)
			{
				if (tile.items != null)
				{
					for (EventItem item : tile.items)
					{
						if (item == null || item.runeliteIds == null)
						{
							continue;
						}
						for (Integer id : item.runeliteIds)
						{
							if (id == null || names.containsKey(id))
							{
								continue;
							}
							String name = itemName(id);
							if (name != null)
							{
								names.put(id, name);
							}
							try
							{
								images.put(id, itemManager.getImage(id));
							}
							catch (RuntimeException ex)
							{
								log.debug("Item image {}", id, ex);
							}
						}
					}
				}
				if (pictures.containsKey(tile.id))
				{
					continue;
				}
				Integer id = TileIcons.runeLiteId(tile);
				if (id == null)
				{
					continue;
				}
				try
				{
					TileIcons.Picture picture = TileIcons.picture(tile, itemManager.getImage(id), true);
					if (picture != null)
					{
						pictures.put(tile.id, picture);
					}
				}
				catch (RuntimeException ex)
				{
					log.debug("Item image {}", id, ex);
				}
			}
			SwingUtilities.invokeLater(() -> panel.showBoard(loaded, names, images, pictures));
		});
	}

	private static Map<Long, TileIcons.Picture> picturesFor(List<EventTile> boardTiles, Map<String, BufferedImage> images)
	{
		Map<Long, TileIcons.Picture> pictures = new HashMap<>();
		if (images == null)
		{
			return pictures;
		}
		for (EventTile tile : boardTiles)
		{
			if (tile == null)
			{
				continue;
			}
			String primary = TileIcons.imageUrl(tile);
			BufferedImage image = primary == null ? null : images.get(primary);
			boolean catalog = false;
			if (image == null)
			{
				String fallback = TileIcons.catalogIconUrl(tile);
				if (fallback != null && !fallback.equals(primary))
				{
					image = images.get(fallback);
					catalog = image != null;
				}
			}
			TileIcons.Picture picture = TileIcons.picture(tile, image, catalog);
			if (picture != null)
			{
				pictures.put(tile.id, picture);
			}
		}
		return pictures;
	}

	private void finishImage(Call call, CompletableFuture<Void> job)
	{
		synchronized (imageCalls)
		{
			imageCalls.remove(call);
		}
		job.complete(null);
	}

	private void cancelImages()
	{
		List<Call> calls;
		synchronized (imageCalls)
		{
			calls = new ArrayList<>(imageCalls);
			imageCalls.clear();
		}
		for (Call call : calls)
		{
			call.cancel();
		}
	}

	private static BufferedImage readImage(Response response) throws IOException
	{
		if (!response.isSuccessful() || response.body() == null || !TileIcons.allowed(response.request().url().toString()))
		{
			return null;
		}
		try (InputStream in = response.body().byteStream())
		{
			byte[] bytes = readAtMost(in, MAX_IMAGE_BYTES);
			if (bytes == null)
			{
				return null;
			}
			BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
			if (image == null || image.getWidth() < 1 || image.getHeight() < 1 || image.getWidth() > 1024 || image.getHeight() > 1024)
			{
				return null;
			}
			return image;
		}
	}

	private static byte[] readAtMost(InputStream in, int max) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int total = 0;
		int read;
		while ((read = in.read(buf)) >= 0)
		{
			if (total > max - read)
			{
				return null;
			}
			out.write(buf, 0, read);
			total += read;
		}
		return out.toByteArray();
	}

	private String errorMessage(String body)
	{
		try
		{
			ErrorBody error = gson.fromJson(body, ErrorBody.class);
			if (error != null && error.error != null && !error.error.isEmpty())
			{
				return error.error;
			}
		}
		catch (RuntimeException ignored)
		{
		}
		return "Could not fetch event";
	}

	private static String passwordOf(EventBoard board)
	{
		if (board == null || board.eventPassword == null)
		{
			return "";
		}
		return board.eventPassword.trim();
	}

	private static List<EventPlayer> copyPlayers(EventBoard board)
	{
		if (board == null || board.players == null)
		{
			return List.of();
		}
		List<EventPlayer> copy = new ArrayList<>();
		for (EventPlayer player : board.players)
		{
			if (player != null)
			{
				copy.add(player);
			}
		}
		return List.copyOf(copy);
	}

	private static final class ErrorBody
	{
		private String error;
	}
}
