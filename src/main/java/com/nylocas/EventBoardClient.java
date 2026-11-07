package com.nylocas;

import com.google.gson.Gson;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
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
import java.util.function.Supplier;
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
	private final Supplier<String> loggedInName;

	private static final int MAX_IMAGE_BYTES = 1 << 20;

	private final AtomicInteger generation = new AtomicInteger();
	private final Map<String, BufferedImage> imageCache = new ConcurrentHashMap<>();
	private final List<Call> imageCalls = new ArrayList<>();
	private Call fetchCall;
	private volatile boolean running;
	private String sessionKey = "";
	private volatile List<EventTile> tiles = List.of();
	private volatile List<EventPlayer> players = List.of();
	private volatile String eventName = "";
	private volatile String teamName = "";
	private volatile String eventCode = "";
	private volatile String discordWebhookUrl = "";
	private volatile boolean eventLoaded;
	private volatile boolean eventStarted;
	private volatile Instant startsAt;
	private volatile Instant endsAt;

	EventBoardClient(OkHttpClient okHttpClient, Gson gson, ClientThread clientThread, ItemManager itemManager, Supplier<String> loggedInName)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.loggedInName = loggedInName;
	}

	void start()
	{
		running = true;
	}

	void noteProgress(long tileId, String itemKey, int approved, int pending, boolean complete)
	{
		int ticket = generation.get();
		clientThread.invoke(() ->
		{
			if (!running || ticket != generation.get())
			{
				return;
			}
			applyServerProgress(tiles, tileId, itemKey, approved, pending, complete);
		});
	}

	void releaseReservation(long tileId, String itemKey)
	{
		clientThread.invoke(() -> releaseHold(tiles, tileId, itemKey));
	}

	void acknowledgeReservation(long tileId, String itemKey)
	{
		clientThread.invoke(() -> acknowledgeHold(tiles, tileId, itemKey));
	}

	static void hold(EventItem item)
	{
		if (item == null)
		{
			return;
		}
		if (item.reserved == 0)
		{
			item.holdBase = item.approved + item.pending;
		}
		item.reserved++;
	}

	static void applyServerProgress(List<EventTile> tiles, long tileId, String itemKey, int approved, int pending, boolean complete)
	{
		for (EventTile tile : tiles)
		{
			if (tile == null || tile.id != tileId)
			{
				continue;
			}
			if (tile.items != null)
			{
				for (EventItem item : tile.items)
				{
					if (item != null && itemKey != null && itemKey.equals(item.key))
					{
						retireAcked(item, approved + pending);
						item.approved = approved;
						item.pending = pending;
					}
				}
			}
			tile.complete = complete;
			tile.partial = !complete && hasApproved(tile);
			return;
		}
	}

	static boolean releaseHold(List<EventTile> tiles, long tileId, String itemKey)
	{
		EventItem item = heldItem(tiles, tileId, itemKey);
		if (item == null || item.reserved <= item.acked)
		{
			return false;
		}
		item.reserved--;
		if (item.reserved == 0)
		{
			item.acked = 0;
			item.holdBase = 0;
		}
		return true;
	}

	static void acknowledgeHold(List<EventTile> tiles, long tileId, String itemKey)
	{
		EventItem item = heldItem(tiles, tileId, itemKey);
		if (item == null || item.reserved <= item.acked)
		{
			return;
		}
		item.acked++;
		retireAcked(item, item.approved + item.pending);
	}

	static void carryReservations(List<EventTile> from, List<EventTile> to)
	{
		if (from == null || to == null)
		{
			return;
		}
		Map<Long, Map<String, EventItem>> held = new HashMap<>();
		for (EventTile tile : from)
		{
			if (tile == null || tile.items == null)
			{
				continue;
			}
			for (EventItem item : tile.items)
			{
				if (item == null || item.key == null || item.reserved <= 0)
				{
					continue;
				}
				Map<String, EventItem> items = held.computeIfAbsent(tile.id, id -> new HashMap<>());
				EventItem existing = items.get(item.key);
				if (existing == null)
				{
					EventItem copy = new EventItem();
					copy.reserved = item.reserved;
					copy.acked = item.acked;
					copy.holdBase = item.holdBase;
					items.put(item.key, copy);
					continue;
				}
				existing.reserved += item.reserved;
				existing.acked += item.acked;
				existing.holdBase = Math.max(existing.holdBase, item.holdBase);
			}
		}
		for (EventTile tile : to)
		{
			if (tile == null || tile.items == null)
			{
				continue;
			}
			Map<String, EventItem> items = held.get(tile.id);
			if (items == null)
			{
				continue;
			}
			for (EventItem item : tile.items)
			{
				if (item == null || item.key == null)
				{
					continue;
				}
				EventItem carry = items.remove(item.key);
				if (carry == null)
				{
					continue;
				}
				item.reserved = carry.reserved;
				item.acked = carry.acked;
				item.holdBase = carry.holdBase;
				retireAcked(item, item.approved + item.pending);
			}
		}
	}

	private static EventItem heldItem(List<EventTile> tiles, long tileId, String itemKey)
	{
		if (tiles == null || itemKey == null)
		{
			return null;
		}
		EventItem first = null;
		for (EventTile tile : tiles)
		{
			if (tile == null || tile.id != tileId || tile.items == null)
			{
				continue;
			}
			for (EventItem item : tile.items)
			{
				if (item == null || !itemKey.equals(item.key))
				{
					continue;
				}
				if (first == null)
				{
					first = item;
				}
				if (item.reserved > item.acked)
				{
					return item;
				}
			}
		}
		return first;
	}

	// An upload that already succeeded stays reserved until the server total grows past the total at reserve time.
	private static void retireAcked(EventItem item, int serverSum)
	{
		if (item.acked <= 0 || serverSum <= item.holdBase)
		{
			return;
		}
		int retire = Math.min(item.acked, serverSum - item.holdBase);
		item.acked -= retire;
		item.reserved -= retire;
		if (item.reserved < item.acked)
		{
			item.reserved = item.acked;
		}
		if (item.reserved < 0)
		{
			item.reserved = 0;
			item.acked = 0;
		}
		item.holdBase = serverSum;
	}

	private static boolean hasApproved(EventTile tile)
	{
		if (tile.items == null)
		{
			return false;
		}
		for (EventItem item : tile.items)
		{
			if (item != null && item.approved > 0)
			{
				return true;
			}
		}
		return false;
	}

	void clearSession()
	{
		generation.incrementAndGet();
		if (fetchCall != null)
		{
			fetchCall.cancel();
			fetchCall = null;
		}
		cancelImages();
		tiles = List.of();
		players = List.of();
		eventName = "";
		teamName = "";
		eventCode = "";
		discordWebhookUrl = "";
		eventLoaded = false;
		eventStarted = false;
		startsAt = null;
		endsAt = null;
		sessionKey = "";
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
		teamName = "";
		eventCode = "";
		discordWebhookUrl = "";
		eventLoaded = false;
		eventStarted = false;
		startsAt = null;
		endsAt = null;
		sessionKey = "";
	}

	void refresh(EventPanel panel, int openRow, int rowTicket)
	{
		String key = sessionKey;
		if (!running || panel == null || key == null || key.isEmpty())
		{
			if (panel != null)
			{
				SwingUtilities.invokeLater(panel::rowRefreshFailed);
			}
			return;
		}
		int ticket = generation.get();
		Request request = new Request.Builder()
			.url(NylocasUrls.pluginEvent(key, openRow))
			.header("Accept", "application/json")
			.build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException error)
			{
				if (call.isCanceled() || !running || ticket != generation.get())
				{
					return;
				}
				log.debug("Refresh event failed", error);
				SwingUtilities.invokeLater(panel::rowRefreshFailed);
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
					if (!response.isSuccessful())
					{
						log.debug("Refresh event {}", response.code());
						SwingUtilities.invokeLater(panel::rowRefreshFailed);
						return;
					}
					deliver(panel, gson.fromJson(body, EventBoard.class), ticket, false, rowTicket);
				}
			}
		});
	}

	private void deliver(EventPanel panel, EventBoard board, int ticket, boolean reopen, int rowTicket)
	{
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
		// The panel keeps the fetched tiles. Proof holds are copied onto the matcher list on the client thread.
		clientThread.invoke(() -> installTiles(next, ticket));
		players = copyPlayers(board);
		eventName = board == null || board.eventName == null ? "" : board.eventName;
		teamName = board == null || board.teamName == null ? "" : board.teamName;
		eventCode = codeOf(board);
		discordWebhookUrl = webhookOf(board);
		eventStarted = board != null && board.eventStarted;
		startsAt = EventTime.parse(board == null ? null : board.startsAt);
		endsAt = EventTime.parse(board == null ? null : board.endsAt);
		eventLoaded = true;
		if (!running || ticket != generation.get())
		{
			return;
		}
		loadTileImages(next, ticket, imagesByUrl -> showLoaded(panel, board, next, imagesByUrl, ticket, reopen, rowTicket));
	}

	private void installTiles(List<EventTile> fetched, int ticket)
	{
		if (!running || ticket != generation.get() || fetched == null)
		{
			return;
		}
		List<EventTile> installed = copyTiles(fetched);
		carryReservations(tiles, installed);
		tiles = installed;
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

	String teamName()
	{
		return teamName;
	}

	String eventCode()
	{
		return eventCode;
	}

	String discordWebhookUrl()
	{
		return discordWebhookUrl;
	}

	void noteEventCode(String code)
	{
		eventCode = code == null ? "" : code.trim();
		eventStarted = true;
	}

	boolean eventLoaded()
	{
		return eventLoaded;
	}

	boolean eventStarted()
	{
		return eventStarted;
	}

	Instant startsAt()
	{
		return startsAt;
	}

	Instant endsAt()
	{
		return endsAt;
	}

	boolean beforeStart()
	{
		return EventTime.beforeStart(startsAt, Instant.now());
	}

	boolean afterEnd()
	{
		return EventTime.afterEnd(endsAt, Instant.now());
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
			panel.showStatus("Enable 'Connect to nylocas.com'");
			return false;
		}
		sessionKey = key;
		int ticket = generation.incrementAndGet();
		cancelImages();
		if (fetchCall != null)
		{
			fetchCall.cancel();
		}
		Request request = new Request.Builder()
			.url(NylocasUrls.pluginEvent(key))
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
					clientThread.invoke(() -> acceptFetched(panel, board, ticket));
				}
			}
		});
		return true;
	}

	private void acceptFetched(EventPanel panel, EventBoard board, int ticket)
	{
		if (!running || ticket != generation.get())
		{
			return;
		}
		if (EventPlayer.playerId(board == null ? null : board.players, loggedInName.get()) == 0)
		{
			SwingUtilities.invokeLater(() ->
			{
				if (running && ticket == generation.get())
				{
					panel.notOnTeam();
				}
			});
			return;
		}
		deliver(panel, board, ticket, true, 0);
	}

	private void loadTileImages(List<EventTile> boardTiles, int ticket, Consumer<Map<String, BufferedImage>> done)
	{
		if (!running || ticket != generation.get())
		{
			return;
		}
		Map<String, BufferedImage> images = new ConcurrentHashMap<>();
		Set<String> urls = new LinkedHashSet<>();
		for (EventTile tile : boardTiles)
		{
			String primary = TileImages.imageUrl(tile);
			if (TileImages.allowed(primary))
			{
				urls.add(primary);
			}
			String catalog = TileImages.catalogIconUrl(tile);
			if (catalog != null && !catalog.equals(primary) && TileImages.allowed(catalog))
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

	private void showLoaded(EventPanel panel, EventBoard loaded, List<EventTile> boardTiles, Map<String, BufferedImage> imagesByUrl, int ticket, boolean reopen, int rowTicket)
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
			Map<Long, TileImages.Image> tileImages = tileImagesFor(boardTiles, imagesByUrl);
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
				if (tileImages.containsKey(tile.id))
				{
					continue;
				}
				Integer id = TileImages.runeLiteId(tile);
				if (id == null)
				{
					continue;
				}
				try
				{
					TileImages.Image tileImage = TileImages.image(tile, itemManager.getImage(id), true);
					if (tileImage != null)
					{
						tileImages.put(tile.id, tileImage);
					}
				}
				catch (RuntimeException ex)
				{
					log.debug("Item image {}", id, ex);
				}
			}
			SwingUtilities.invokeLater(() -> panel.showBoard(loaded, names, images, tileImages, reopen, rowTicket));
		});
	}

	private static Map<Long, TileImages.Image> tileImagesFor(List<EventTile> boardTiles, Map<String, BufferedImage> images)
	{
		Map<Long, TileImages.Image> tileImages = new HashMap<>();
		if (images == null)
		{
			return tileImages;
		}
		for (EventTile tile : boardTiles)
		{
			if (tile == null)
			{
				continue;
			}
			String primary = TileImages.imageUrl(tile);
			BufferedImage image = primary == null ? null : images.get(primary);
			boolean catalog = false;
			if (image == null)
			{
				String fallback = TileImages.catalogIconUrl(tile);
				if (fallback != null && !fallback.equals(primary))
				{
					image = images.get(fallback);
					catalog = image != null;
				}
			}
			TileImages.Image tileImage = TileImages.image(tile, image, catalog);
			if (tileImage != null)
			{
				tileImages.put(tile.id, tileImage);
			}
		}
		return tileImages;
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
		if (!response.isSuccessful() || response.body() == null || !TileImages.allowed(response.request().url().toString()))
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
		String message = ErrorBody.message(gson, body);
		return message.isEmpty() ? "Could not fetch event" : message;
	}

	private static String codeOf(EventBoard board)
	{
		if (board == null || board.eventCode == null)
		{
			return "";
		}
		return board.eventCode.trim();
	}

	private static String webhookOf(EventBoard board)
	{
		if (board == null || board.discordWebhookUrl == null)
		{
			return "";
		}
		return board.discordWebhookUrl.trim();
	}

	static List<EventTile> copyTiles(List<EventTile> source)
	{
		List<EventTile> copy = new ArrayList<>(source.size());
		for (EventTile tile : source)
		{
			copy.add(copyTile(tile));
		}
		return List.copyOf(copy);
	}

	private static EventTile copyTile(EventTile tile)
	{
		EventTile copy = new EventTile();
		copy.id = tile.id;
		copy.row = tile.row;
		copy.col = tile.col;
		copy.title = tile.title;
		copy.iconItem = tile.iconItem;
		copy.imageUrl = tile.imageUrl;
		copy.imageFit = tile.imageFit;
		copy.iconCount = tile.iconCount;
		copy.complete = tile.complete;
		copy.partial = tile.partial;
		copy.need = tile.need;
		copy.pool = tile.pool;
		if (tile.items == null)
		{
			return copy;
		}
		List<EventItem> items = new ArrayList<>(tile.items.size());
		for (EventItem item : tile.items)
		{
			items.add(item == null ? null : copyItem(item));
		}
		copy.items = items;
		return copy;
	}

	private static EventItem copyItem(EventItem item)
	{
		EventItem copy = new EventItem();
		copy.key = item.key;
		copy.count = item.count;
		copy.approved = item.approved;
		copy.pending = item.pending;
		copy.runeliteIds = item.runeliteIds == null ? null : new ArrayList<>(item.runeliteIds);
		copy.sources = item.sources == null ? null : new ArrayList<>(item.sources);
		return copy;
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
}
