package com.nylocas;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
	name = "Nylocas Clan Events",
	description = "Nylocas Clan Events RuneLite integration",
	internalName = "nylocas-clan-events"
)
public class NylocasPlugin extends Plugin
{
	static final String API_BASE = "http://127.0.0.1:8080";
	private static final String TEAM_KEY_FILE = "team-key";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private NylocasConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ScheduledExecutorService executor;

	private final Object keyFileLock = new Object();
	private NavigationButton navButton;
	private volatile EventPanel panel;
	private Call fetchCall;
	private volatile boolean running;
	private volatile List<EventTile> tiles = List.of();
	private String raidChatName;
	private long raidChatAt;

	@Provides
	NylocasConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NylocasConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		running = true;
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "icon.png");
		panel = new EventPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Nylocas Clan Events")
			.icon(icon)
			.priority(10)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		executor.execute(this::migrateTeamKey);
		log.debug("Nylocas started");
	}

	@Override
	protected void shutDown() throws Exception
	{
		running = false;
		if (fetchCall != null)
		{
			fetchCall.cancel();
			fetchCall = null;
		}
		tiles = List.of();
		raidChatName = null;
		EventPanel current = panel;
		panel = null;
		if (current != null)
		{
			current.stop();
		}
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		log.debug("Nylocas stopped");
	}

	String teamKey()
	{
		String key = config.teamKey();
		return key == null ? "" : key.trim();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!NylocasConfig.GROUP.equals(event.getGroup()) || !NylocasConfig.TEAM_KEY.equals(event.getKey()))
		{
			return;
		}
		SwingUtilities.invokeLater(this::refreshPanelKey);
	}

	private void refreshPanelKey()
	{
		EventPanel current = panel;
		if (running && current != null)
		{
			current.onTeamKeyChanged();
		}
	}

	private void migrateTeamKey()
	{
		if (!running)
		{
			return;
		}
		if (teamKey().isEmpty())
		{
			String legacy = readTeamKey();
			if (legacy == null)
			{
				return;
			}
			if (!legacy.isEmpty() && running)
			{
				configManager.setConfiguration(NylocasConfig.GROUP, NylocasConfig.TEAM_KEY, legacy);
			}
		}
		if (!running)
		{
			return;
		}
		deleteTeamKeyFile();
		SwingUtilities.invokeLater(this::refreshPanelKey);
	}

	private String readTeamKey()
	{
		synchronized (keyFileLock)
		{
			try
			{
				Filepath file = teamKeyFile();
				if (!file.isFile())
				{
					return "";
				}
				try (BufferedReader reader = file.openBufferedReader())
				{
					String line = reader.readLine();
					return line == null ? "" : line.trim();
				}
			}
			catch (IOException ex)
			{
				log.warn("Could not read the saved team key", ex);
				return null;
			}
		}
	}

	private void deleteTeamKeyFile()
	{
		synchronized (keyFileLock)
		{
			try
			{
				teamKeyFile().deleteIfExists();
			}
			catch (IOException ex)
			{
				log.warn("Could not remove the saved team key file", ex);
			}
		}
	}

	private Filepath teamKeyFile() throws IOException
	{
		return getPluginDirectory().joinSegment(TEAM_KEY_FILE);
	}

	boolean fetchEvent(String key, EventPanel view)
	{
		if (!config.connect())
		{
			view.showStatus("Turn on Connect to nylocas.com in the plugin settings");
			return false;
		}
		if (fetchCall != null)
		{
			fetchCall.cancel();
		}
		Request request = new Request.Builder()
			.url(API_BASE + "/plugin/event")
			.header("Authorization", "Bearer " + key)
			.header("Accept", "application/json")
			.build();
		fetchCall = okHttpClient.newCall(request);
		fetchCall.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException error)
			{
				if (call.isCanceled())
				{
					return;
				}
				log.debug("Fetch event failed", error);
				SwingUtilities.invokeLater(() -> view.showStatus("Could not fetch event"));
			}

			@Override
			public void onResponse(Call call, Response response) throws IOException
			{
				try (Response ignored = response)
				{
					String body = response.body() == null ? "" : response.body().string();
					if (!response.isSuccessful())
					{
						String message = errorMessage(body);
						SwingUtilities.invokeLater(() -> view.showStatus(message));
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
					tiles = List.copyOf(next);
					EventBoard loaded = board;
					clientThread.invoke(() ->
					{
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
						SwingUtilities.invokeLater(() -> view.showBoard(loaded, names, images));
					});
				}
			}
		});
		return true;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		String name = RaidSource.fromKillCount(Text.removeTags(event.getMessage()));
		if (name == null)
		{
			return;
		}
		raidChatName = name;
		raidChatAt = System.currentTimeMillis();
	}

	@Subscribe
	public void onLootReceived(LootReceived lootReceived)
	{
		String sourceName = lootSource(lootReceived.getName());
		if (sourceName == null)
		{
			return;
		}
		for (ItemStack item : lootReceived.getItems())
		{
			if (AllowedDrops.matchingTitles(tiles, sourceName, item.getId()).isEmpty())
			{
				continue;
			}
			String name = itemName(item.getId());
			if (name == null)
			{
				name = Integer.toString(item.getId());
			}
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", receivedMessage(name, sourceName), null);
			log.debug("Allowed drop {} from {}", name, sourceName);
		}
	}

	private static String receivedMessage(String item, String source)
	{
		return "received: " + ColorUtil.wrapWithColorTag(item, Color.RED)
			+ ", from: " + ColorUtil.wrapWithColorTag(source, Color.RED);
	}

	private String lootSource(String lootName)
	{
		String chat = raidChatName;
		if (chat != null && System.currentTimeMillis() - raidChatAt > RaidSource.KILL_COUNT_MAX_AGE_MS)
		{
			chat = null;
		}
		String source = RaidSource.resolve(lootName, chat);
		if (RaidSource.isChest(lootName))
		{
			raidChatName = null;
		}
		return source;
	}

	private String itemName(int id)
	{
		String name = itemManager.getItemComposition(id).getName();
		if (name == null || name.isEmpty() || "null".equals(name))
		{
			return null;
		}
		return name;
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

	private static final class ErrorBody
	{
		private String error;
	}
}
