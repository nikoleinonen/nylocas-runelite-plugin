package com.nylocas;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
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
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
	name = "Nylocas Clan Events",
	description = "nylocas.com RuneLite integration",
	internalName = "nylocas-clan-events"
)
public class NylocasPlugin extends Plugin
{
	static final String API_BASE = "https://api.nylocas.com";
	private static final String TEAM_KEY_FILE = "team-key";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

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
	private EventCodeOverlay overlay;
	private volatile EventPanel panel;
	private volatile boolean running;
	private EventBoardClient board;
	private EventWebsocket websocket;
	private ProofUploader proofs;
	private final AtomicInteger sessionGeneration = new AtomicInteger();
	private volatile boolean proofsStopped;
	private volatile ScheduledFuture<?> eventEndTask;
	private volatile ScheduledFuture<?> realtimeTask;
	private final AtomicInteger realtimeGeneration = new AtomicInteger();
	private static final long REALTIME_QUIET_MS = 1000;
	private String raidChatName;
	private long raidChatAt;
	private final Map<String, CompletionSeen> completionCounts = new HashMap<>();
	private final ArrayDeque<Long> announcedDrops = new ArrayDeque<>();
	private static final int ANNOUNCED_DROP_CAP = 32;
	private static final MediaType DROP_JSON = MediaType.parse("application/json");
	private boolean loggingIn;
	private boolean awaitLogin;
	private volatile boolean loginScreen;

	@Provides
	NylocasConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NylocasConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		running = true;
		loginScreen = client.getGameState() == GameState.LOGIN_SCREEN;
		board = new EventBoardClient(okHttpClient, gson, clientThread, itemManager, this::localPlayerName);
		websocket = new EventWebsocket(okHttpClient, gson, executor, this);
		proofs = new ProofUploader(client, clientThread, drawManager, executor, okHttpClient, gson, this::sessionId, this::eventAfterEnd, this::onProofEventEnded);
		board.start();
		proofs.start();
		overlay = new EventCodeOverlay(config, this);
		overlayManager.add(overlay);
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
		loggingIn = false;
		awaitLogin = false;
		cancelEventEnd();
		cancelRealtime();
		if (overlay != null)
		{
			overlayManager.remove(overlay);
			overlay = null;
		}
		if (websocket != null)
		{
			websocket.stop();
		}
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

	String eventCode()
	{
		EventBoardClient current = board;
		return current == null ? "" : current.eventCode();
	}

	boolean eventLoaded()
	{
		EventBoardClient current = board;
		return current != null && current.eventLoaded();
	}

	boolean eventStarted()
	{
		EventBoardClient current = board;
		return current != null && current.eventStarted();
	}

	Instant eventStartsAt()
	{
		EventBoardClient current = board;
		return current == null ? null : current.startsAt();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		boolean screen = state == GameState.LOGIN_SCREEN;
		if (screen != loginScreen)
		{
			loginScreen = screen;
			if (!screen)
			{
				SwingUtilities.invokeLater(this::refreshSessionButton);
			}
		}
		if (state == GameState.LOGGING_IN)
		{
			loggingIn = true;
			awaitLogin = false;
			return;
		}
		if (state == GameState.LOGGED_IN && loggingIn)
		{
			loggingIn = false;
			awaitLogin = true;
			return;
		}
		if (state == GameState.LOGIN_SCREEN)
		{
			loggingIn = false;
			awaitLogin = false;
			if (websocket != null && websocket.isLive())
			{
				websocket.stop();
			}
			SwingUtilities.invokeLater(this::closeEventForLogout);
			return;
		}
		if (state == GameState.HOPPING)
		{
			loggingIn = false;
			awaitLogin = false;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!awaitLogin || !running || client.getLocalPlayer() == null)
		{
			return;
		}
		awaitLogin = false;
		onLogin();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!NylocasConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if (NylocasConfig.TEAM_KEY.equals(event.getKey()))
		{
			SwingUtilities.invokeLater(this::refreshPanelKey);
			return;
		}
		if (NylocasConfig.CONNECT_WEBSOCKET.equals(event.getKey()))
		{
			SwingUtilities.invokeLater(this::refreshSessionButton);
			scheduleRealtime();
			return;
		}
		if (NylocasConfig.RECEIVE_ENEMY.equals(event.getKey())
			|| NylocasConfig.RECEIVE_ENEMY_REJECTIONS.equals(event.getKey())
			|| NylocasConfig.ENEMY_EVENT_DROP.equals(event.getKey()))
		{
			SwingUtilities.invokeLater(this::pushEnemyFilters);
		}
	}

	private void refreshPanelKey()
	{
		EventPanel current = panel;
		if (running && current != null)
		{
			current.onTeamKeyChanged();
		}
	}

	private void refreshSessionButton()
	{
		EventPanel current = panel;
		if (running && current != null)
		{
			current.onWebsocketChanged();
		}
	}

	private void closeEventForLogout()
	{
		EventPanel current = panel;
		if (running && current != null)
		{
			current.closeBecauseLoggedOut();
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

	boolean websocketEnabled()
	{
		return config.connectWebsocket();
	}

	boolean atLoginScreen()
	{
		return loginScreen;
	}

	void refreshEvent(int openRow, int rowTicket)
	{
		if (board != null)
		{
			board.refresh(panel, openRow, rowTicket);
		}
	}

	private String teamName()
	{
		return board == null || board.teamName() == null ? "" : board.teamName();
	}

	void endSession()
	{
		sessionGeneration.incrementAndGet();
		cancelEventEnd();
		cancelRealtime();
		if (proofs != null)
		{
			proofs.cancelUploads();
		}
		clearAnnounced();
		if (websocket != null)
		{
			websocket.stop();
		}
		if (board != null)
		{
			board.clearSession();
		}
	}

	void noteSession(EventBoard event)
	{
		int ticket = sessionGeneration.incrementAndGet();
		proofsStopped = false;
		scheduleEventEnd(event == null ? null : event.endsAt, ticket);
	}

	void websocketEnded(boolean eventOver)
	{
		if (!running)
		{
			return;
		}
		if (eventOver)
		{
			proofsStopped = true;
			endEventSession();
			return;
		}
		EventPanel current = panel;
		if (current != null)
		{
			current.socketFinished();
		}
	}

	void refreshEnemyFilters(EventWebsocket socket)
	{
		socket.useEnemyFilters(config.receiveEnemy(), config.receiveEnemyRejections(), config.enemyEventDrop());
	}

	private void pushEnemyFilters()
	{
		if (!running || websocket == null)
		{
			return;
		}
		websocket.pushEnemyFilters(config.receiveEnemy(), config.receiveEnemyRejections(), config.enemyEventDrop());
	}

	private void scheduleRealtime()
	{
		int ticket = realtimeGeneration.incrementAndGet();
		ScheduledFuture<?> previous = realtimeTask;
		realtimeTask = executor.schedule(() ->
		{
			if (ticket != realtimeGeneration.get())
			{
				return;
			}
			SwingUtilities.invokeLater(() -> applyRealtime(ticket));
		}, REALTIME_QUIET_MS, TimeUnit.MILLISECONDS);
		if (previous != null)
		{
			previous.cancel(false);
		}
	}

	private void cancelRealtime()
	{
		realtimeGeneration.incrementAndGet();
		ScheduledFuture<?> task = realtimeTask;
		realtimeTask = null;
		if (task != null)
		{
			task.cancel(false);
		}
	}

	private void applyRealtime(int ticket)
	{
		if (!running || ticket != realtimeGeneration.get() || websocket == null || board == null)
		{
			return;
		}
		EventPanel current = panel;
		if (current == null || !current.sessionUp())
		{
			return;
		}
		if (!config.connectWebsocket())
		{
			if (websocket.isLive())
			{
				websocket.stop();
			}
			return;
		}
		if (websocket.isLive() || board.afterEnd())
		{
			return;
		}
		Instant starts = board.startsAt();
		Instant ends = board.endsAt();
		if (starts == null || ends == null)
		{
			return;
		}
		EventBoard timing = new EventBoard();
		timing.startsAt = starts.toString();
		timing.endsAt = ends.toString();
		openWebsocket(timing);
	}

	void openWebsocket(EventBoard event)
	{
		if (websocket == null)
		{
			return;
		}
		if (!config.connectWebsocket())
		{
			websocket.stop();
			return;
		}
		if (event == null)
		{
			return;
		}
		websocket.open(teamKey(), true, true, config.receiveEnemy(), config.receiveEnemyRejections(), config.enemyEventDrop(), event.startsAt, event.endsAt);
	}

	private void onLocalEventEnd(int ticket)
	{
		if (!running || ticket != sessionGeneration.get())
		{
			return;
		}
		proofsStopped = true;
		if (websocket != null && websocket.isLive())
		{
			return;
		}
		endEventSession();
	}

	private void onProofEventEnded(int session)
	{
		if (!running || session != sessionGeneration.get())
		{
			return;
		}
		proofsStopped = true;
		if (websocket != null && websocket.isLive())
		{
			return;
		}
		endEventSession();
	}

	private void endEventSession()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (!running)
			{
				return;
			}
			EventPanel current = panel;
			if (current == null || !current.clearBecauseEventEnded())
			{
				return;
			}
			chat(ProofMessages.EVENT_ENDED);
		});
	}

	private void scheduleEventEnd(String endsAt, int ticket)
	{
		cancelEventEnd();
		Instant end = EventClock.parse(endsAt);
		if (end == null)
		{
			return;
		}
		long delay = Math.max(0L, end.toEpochMilli() - Instant.now().toEpochMilli());
		eventEndTask = executor.schedule(() -> onLocalEventEnd(ticket), delay, TimeUnit.MILLISECONDS);
	}

	private void cancelEventEnd()
	{
		ScheduledFuture<?> task = eventEndTask;
		eventEndTask = null;
		if (task != null)
		{
			task.cancel(false);
		}
	}

	private int sessionId()
	{
		return sessionGeneration.get();
	}

	private boolean eventAfterEnd()
	{
		EventBoardClient current = board;
		return current != null && current.afterEnd();
	}

	void onWebsocketFrame(EventWebsocket.Frame frame)
	{
		if (!running || frame == null || frame.type == null)
		{
			return;
		}
		if ("progress".equals(frame.type))
		{
			boolean complete = Boolean.TRUE.equals(frame.complete);
			if (frame.approval && config.receiveOwn())
			{
				chat(WebsocketText.approved(teamName(), tileTitle(frame.tileId), complete, accentColor(true), config.tileColor()));
			}
			else if ("reject".equals(frame.action) || "delete".equals(frame.action))
			{
				if (config.receiveOwnRejections())
				{
					chatReview(frame, true);
				}
			}
			EventPanel current = panel;
			if (current != null)
			{
				current.applyProgress(frame.tileId, frame.item, frame.approved, frame.pending, complete);
				if (frame.openRow != null)
				{
					current.noteOpenRow(frame.openRow);
				}
			}
			if (board != null)
			{
				board.noteProgress(frame.tileId, frame.item, frame.approved, frame.pending, complete);
			}
			return;
		}
		if ("approved".equals(frame.type))
		{
			String team = frame.teamName == null ? "" : frame.teamName;
			boolean complete = Boolean.TRUE.equals(frame.complete);
			chat(WebsocketText.approved(team, enemyTileTitle(frame), complete, accentColor(false), config.tileColor()));
			return;
		}
		if ("dropped".equals(frame.type))
		{
			if (consumeAnnounce(frame.id))
			{
				return;
			}
			String team = frame.teamName == null ? "" : frame.teamName;
			boolean own = !team.isEmpty() && team.equals(teamName());
			if (own && !config.eventDrop())
			{
				return;
			}
			chat(WebsocketText.teamEventDrop(team, itemLabel(frame), enemyTileTitle(frame), accentColor(own), config.dropColor(), config.tileColor()));
			return;
		}
		if ("rejected".equals(frame.type) || "deleted".equals(frame.type))
		{
			chatReview(frame, false);
			return;
		}
		if ("code".equals(frame.type))
		{
			EventBoardClient current = board;
			if (current != null)
			{
				current.noteEventCode(frame.eventCode);
			}
			return;
		}
		if ("rank".equals(frame.type))
		{
			if (config.receiveRank())
			{
				chat(WebsocketText.rank(frame.leader, Boolean.TRUE.equals(frame.winner), accentColor(ownLeader(frame.leader))));
			}
			EventPanel current = panel;
			if (current != null)
			{
				current.showPlace(frame.place);
			}
		}
	}

	private void chatReview(EventWebsocket.Frame frame, boolean own)
	{
		String tile = own ? tileTitle(frame.tileId) : enemyTileTitle(frame);
		String team = own ? teamName() : (frame.teamName == null ? "" : frame.teamName);
		Color teamColor = accentColor(own);
		if (own ? "reject".equals(frame.action) : "rejected".equals(frame.type))
		{
			chat(WebsocketText.rejected(team, tile, frame.rejectionReason, teamColor, config.tileColor(), config.rejectionColor()));
			return;
		}
		if (own ? "delete".equals(frame.action) : "deleted".equals(frame.type))
		{
			chat(WebsocketText.deleted(team, tile, teamColor, config.tileColor()));
		}
	}

	private String enemyTileTitle(EventWebsocket.Frame frame)
	{
		String local = tileTitle(frame.tileId);
		if (!local.isEmpty())
		{
			return local;
		}
		if (frame.title != null && !frame.title.isEmpty())
		{
			return frame.title;
		}
		return "";
	}

	private String tileTitle(long tileId)
	{
		EventPanel current = panel;
		if (current == null)
		{
			return "";
		}
		return current.tileTitle(tileId);
	}

	private String itemLabel(EventWebsocket.Frame frame)
	{
		EventPanel current = panel;
		if (current != null)
		{
			return current.itemLabel(frame.tileId, frame.item);
		}
		return TileText.catalogName(frame.item);
	}

	private Color accentColor(boolean own)
	{
		return own ? config.ownTeamColor() : config.enemyTeamColor();
	}

	private boolean ownLeader(String leader)
	{
		if (leader == null || leader.isEmpty() || board == null)
		{
			return false;
		}
		String ours = board.teamName();
		return ours != null && !ours.isEmpty() && ours.equals(leader);
	}

	private void onLogin()
	{
		if (!running || teamKey().isEmpty())
		{
			return;
		}
		if (!config.autoConnect())
		{
			chat(LoginText.reminder(config.connectWebsocket()));
			return;
		}
		EventPanel current = panel;
		if (current == null)
		{
			return;
		}
		SwingUtilities.invokeLater(current::connectOnLogin);
	}

	void announceSession(EventBoard board, boolean reconnected)
	{
		String eventName = board == null || board.eventName == null ? "" : board.eventName;
		String teamName = board == null || board.teamName == null ? "" : board.teamName;
		if (reconnected)
		{
			chat(LoginText.reconnected(eventName, teamName));
			return;
		}
		if (config.connectWebsocket())
		{
			chat(LoginText.connected(eventName, teamName));
			return;
		}
		chat(LoginText.fetched(eventName, teamName));
	}

	private void chat(String line)
	{
		clientThread.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", line, null));
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());
		CompletionCount count = CompletionCount.parse(message);
		if (count != null)
		{
			completionCounts.put(count.name.toLowerCase(Locale.ROOT), new CompletionSeen(count.count, System.currentTimeMillis()));
		}
		String sourceName = RaidSource.fromKillCount(message);
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
		log.debug("Allowed source {} dropped {}", sourceName, itemNames);
		List<String> labels = dropLabels(tiles, sourceName, lootReceived.getItems());
		if (labels.isEmpty())
		{
			return;
		}
		boolean saveLocally = config.saveProofs();
		boolean live = config.connect() && !proofsStopped && !board.beforeStart() && !board.afterEnd();
		boolean uploadProofs = config.submitProofs() && live;
		List<AllowedDrops.ProofTarget> targets = List.of();
		String proofKey = "";
		long proofPlayerId = 0;
		if (live)
		{
			proofKey = teamKey();
			proofPlayerId = AllowedDrops.playerId(board.players(), localPlayerName());
		}
		if (uploadProofs)
		{
			targets = proofTargets(tiles, sourceName, lootReceived.getItems());
			if (proofKey.isEmpty() || proofPlayerId == 0)
			{
				proofs.releaseReservations(targets);
				proofs.proofFailed();
				targets = List.of();
				uploadProofs = false;
			}
		}
		if (!board.beforeStart())
		{
			notifyEventDrops(tiles, sourceName, lootReceived.getItems(), uploadProofs, targets);
		}
		if (live && !proofKey.isEmpty() && proofPlayerId != 0)
		{
			List<AllowedDrops.ProofTarget> announced = uploadProofs ? targets : matchedTargets(tiles, sourceName, lootReceived.getItems());
			for (AllowedDrops.ProofTarget target : announced)
			{
				announceDrop(target, proofPlayerId, proofKey);
			}
		}
		if (!saveLocally && !uploadProofs)
		{
			return;
		}
		proofs.capture(this::pluginDirectory, board.eventName(), labels, saveLocally, uploadProofs, targets, proofPlayerId, proofKey, completionCount(sourceName), config.hidePrivateMessages());
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

	private void notifyEventDrops(List<EventTile> tiles, String sourceName, Collection<ItemStack> stacks, boolean autoSubmitted, List<AllowedDrops.ProofTarget> submitted)
	{
		if (!config.eventDrop())
		{
			return;
		}
		if (autoSubmitted)
		{
			for (AllowedDrops.ProofTarget target : submitted)
			{
				String dropName = target.itemName == null ? "" : target.itemName;
				chat(WebsocketText.eventDrop(dropName, TileText.tileTitle(target.tile), true, config.dropColor(), config.tileColor()));
			}
			return;
		}
		for (ItemStack stack : stacks)
		{
			AllowedDrops.ProofTarget target = AllowedDrops.firstProof(tiles, sourceName, stack.getId());
			if (target == null)
			{
				continue;
			}
			chat(WebsocketText.eventDrop(itemLabel(stack.getId()), TileText.tileTitle(target.tile), false, config.dropColor(), config.tileColor()));
		}
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

	private List<AllowedDrops.ProofTarget> matchedTargets(List<EventTile> tiles, String sourceName, Collection<ItemStack> stacks)
	{
		List<AllowedDrops.ProofTarget> targets = new ArrayList<>();
		for (ItemStack stack : stacks)
		{
			AllowedDrops.ProofTarget target = AllowedDrops.firstProof(tiles, sourceName, stack.getId());
			if (target != null)
			{
				targets.add(target);
			}
		}
		return targets;
	}

	private void announceDrop(AllowedDrops.ProofTarget target, long playerId, String key)
	{
		if (target == null || target.tile == null || target.item == null || target.item.key == null || target.item.key.isEmpty() || target.sourceId == null || target.sourceId.isEmpty())
		{
			return;
		}
		long id = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
		rememberAnnounce(id);
		DropRequest request = new DropRequest();
		request.tileId = target.tile.id;
		request.playerId = playerId;
		request.item = target.item.key;
		request.source = target.sourceId;
		request.id = id;
		RequestBody body = RequestBody.create(DROP_JSON, gson.toJson(request));
		Request call = new Request.Builder()
			.url(API_BASE + "/plugin/drop")
			.header("Authorization", "Bearer " + key)
			.post(body)
			.build();
		okHttpClient.newCall(call).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call ignored, IOException error)
			{
				log.debug("Drop announce failed", error);
				forgetAnnounce(id);
			}

			@Override
			public void onResponse(Call ignored, Response response)
			{
				try
				{
					if (response.isSuccessful())
					{
						return;
					}
					log.debug("Drop announce rejected {}", response.code());
					forgetAnnounce(id);
				}
				finally
				{
					response.close();
				}
			}
		});
	}

	private void rememberAnnounce(long id)
	{
		synchronized (announcedDrops)
		{
			announcedDrops.addLast(id);
			while (announcedDrops.size() > ANNOUNCED_DROP_CAP)
			{
				announcedDrops.removeFirst();
			}
		}
	}

	private boolean consumeAnnounce(long id)
	{
		if (id <= 0)
		{
			return false;
		}
		synchronized (announcedDrops)
		{
			return announcedDrops.remove(id);
		}
	}

	private void forgetAnnounce(long id)
	{
		synchronized (announcedDrops)
		{
			announcedDrops.remove(id);
		}
	}

	private void clearAnnounced()
	{
		synchronized (announcedDrops)
		{
			announcedDrops.clear();
		}
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

	private String lootSource(String lootName)
	{
		String chat = raidChatName;
		if (chat != null && !RaidSource.chatNameIsRecent(raidChatAt))
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

	private Integer completionCount(String sourceName)
	{
		if (sourceName == null)
		{
			return null;
		}
		CompletionSeen seen = completionCounts.get(sourceName.toLowerCase(Locale.ROOT));
		if (seen == null || !CompletionCount.isRecent(seen.at))
		{
			return null;
		}
		return seen.count;
	}

	private static final class DropRequest
	{
		@SerializedName("tile_id")
		long tileId;

		@SerializedName("player_id")
		long playerId;

		String item;
		String source;
		long id;
	}

	private static final class CompletionSeen
	{
		final int count;
		final long at;

		CompletionSeen(int count, long at)
		{
			this.count = count;
			this.at = at;
		}
	}
}
