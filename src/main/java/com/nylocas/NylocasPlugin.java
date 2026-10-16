package com.nylocas;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Nylocas Clan Events",
	description = "nylocas.com RuneLite integration",
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
	private DrawManager drawManager;

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
	private volatile boolean running;
	private EventBoardClient board;
	private ProofUploader proofs;
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
		board = new EventBoardClient(okHttpClient, gson, clientThread, itemManager);
		proofs = new ProofUploader(client, clientThread, drawManager, executor, okHttpClient);
		board.start();
		proofs.start();
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "sidebar.png");
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
		if (board != null)
		{
			board.stop();
		}
		if (proofs != null)
		{
			proofs.stop();
		}
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

	boolean fetchEvent(String key, EventPanel panel)
	{
		return board.fetch(key, panel, config.connect());
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		String sourceName = RaidSource.fromKillCount(Text.removeTags(event.getMessage()));
		if (sourceName == null)
		{
			return;
		}
		raidChatName = sourceName;
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
		List<EventTile> tiles = board.tiles();
		if (!AllowedDrops.allowsSource(tiles, sourceName))
		{
			return;
		}
		List<String> itemNames = new ArrayList<>();
		for (ItemStack item : lootReceived.getItems())
		{
			itemNames.add(itemLabel(item.getId()));
		}
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", completedSourceMessage(sourceName, itemNames), null);
		log.debug("Allowed source {} dropped {}", sourceName, itemNames);
		List<String> labels = dropLabels(tiles, sourceName, lootReceived.getItems());
		if (labels.isEmpty())
		{
			return;
		}
		boolean saveLocally = config.saveProofs();
		boolean uploadProofs = config.submitProofs() && config.connect();
		List<AllowedDrops.ProofTarget> targets = List.of();
		String proofKey = "";
		long proofPlayerId = 0;
		if (uploadProofs)
		{
			targets = proofTargets(tiles, sourceName, lootReceived.getItems());
			proofKey = teamKey();
			proofPlayerId = AllowedDrops.playerId(board.players(), localPlayerName());
			if (proofKey.isEmpty() || proofPlayerId == 0)
			{
				proofs.releaseReservations(targets);
				proofs.proofFailed();
				targets = List.of();
				uploadProofs = false;
			}
		}
		if (!saveLocally && !uploadProofs)
		{
			return;
		}
		proofs.capture(this::pluginDirectory, board.eventName(), labels, saveLocally, uploadProofs, targets, proofPlayerId, proofKey);
	}

	private Filepath pluginDirectory() throws IOException
	{
		return getPluginDirectory();
	}

	private String itemLabel(int itemId)
	{
		String label = board.itemName(itemId);
		return label == null ? Integer.toString(itemId) : label;
	}

	private List<String> dropLabels(List<EventTile> tiles, String sourceName, Collection<ItemStack> stacks)
	{
		List<String> labels = new ArrayList<>();
		for (ItemStack stack : stacks)
		{
			if (AllowedDrops.firstProof(tiles, sourceName, stack.getId()) == null)
			{
				continue;
			}
			String label = itemLabel(stack.getId());
			if (!labels.contains(label))
			{
				labels.add(label);
			}
		}
		return labels;
	}

	private List<AllowedDrops.ProofTarget> proofTargets(List<EventTile> tiles, String sourceName, Collection<ItemStack> stacks)
	{
		List<AllowedDrops.ProofTarget> targets = new ArrayList<>();
		for (ItemStack stack : stacks)
		{
			AllowedDrops.ProofTarget target = AllowedDrops.firstProof(tiles, sourceName, stack.getId());
			if (target == null)
			{
				continue;
			}
			target.item.pending++;
			target.itemName = itemLabel(stack.getId());
			targets.add(target);
		}
		return targets;
	}

	private String localPlayerName()
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}
		return client.getLocalPlayer().getName();
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

	private static String completedSourceMessage(String source, List<String> items)
	{
		String message = "Completed allowed source: " + ColorUtil.wrapWithColorTag(source, Color.RED);
		if (!items.isEmpty())
		{
			message += ", item: " + ColorUtil.wrapWithColorTag(String.join(", ", items), Color.RED);
		}
		return message;
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
}
