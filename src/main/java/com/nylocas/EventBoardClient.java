package com.nylocas;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	private Call fetchCall;
	private volatile boolean running;
	private volatile List<EventTile> tiles = List.of();
	private volatile List<EventPlayer> players = List.of();
	private volatile String eventName = "";

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
		if (fetchCall != null)
		{
			fetchCall.cancel();
			fetchCall = null;
		}
		tiles = List.of();
		players = List.of();
		eventName = "";
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
					if (call.isCanceled() || !running)
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
					if (!running)
					{
						return;
					}
					// The panel keeps the fetched tiles. Proof reservations stay on this copy until the next refresh.
					tiles = NylocasPlugin.copyTiles(next);
					players = copyPlayers(board);
					eventName = board == null || board.eventName == null ? "" : board.eventName;
					if (!running)
					{
						tiles = List.of();
						players = List.of();
						eventName = "";
						return;
					}
					EventBoard loaded = board;
					clientThread.invoke(() ->
					{
						if (!running)
						{
							return;
						}
						Map<Integer, String> names = new HashMap<>();
						Map<Integer, AsyncBufferedImage> images = new HashMap<>();
						for (EventTile tile : next)
						{
							if (tile.items == null)
							{
								continue;
							}
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
						SwingUtilities.invokeLater(() -> panel.showBoard(loaded, names, images));
					});
				}
			}
		});
		return true;
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
