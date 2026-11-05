package com.nylocas;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
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
import okhttp3.OkHttpClient;

@Slf4j
@PluginDescriptor(
	name = "Nylocas Clan Events",
	description = "nylocas.com RuneLite integration",
	internalName = "nylocas-clan-events"
)
public class NylocasPlugin extends Plugin
{
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
	private ScheduledExecutorService executor;

	private NavigationButton navButton;
	private EventOverlay overlay;
	private volatile EventPanel panel;
	private volatile boolean running;
	private EventBoardClient board;
	private EventWebsocket websocket;
	private ProofCapture proofs;
	private DropAnnouncer drops;
	private final AtomicInteger sessionGeneration = new AtomicInteger();
	private volatile boolean proofsStopped;
	private volatile ScheduledFuture<?> eventEndTask;
	private volatile ScheduledFuture<?> realtimeTask;
	private final AtomicInteger realtimeGeneration = new AtomicInteger();
	private static final long REALTIME_QUIET_MS = 1000;
	private String raidChatName;
	private long raidChatAt;
	private final Map<String, CompletionSeen> completionCounts = new HashMap<>();
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
		if (drops == null)
		{
			drops = new DropAnnouncer(okHttpClient, gson);
		}
		WebsocketFrames frames = new WebsocketFrames(config, () -> running, () -> panel, () -> board, this::chat, drops::consume);
		websocket = new EventWebsocket(okHttpClient, gson, executor, this, frames, this::enemyFilters);
		proofs = new ProofCapture(client, clientThread, drawManager, executor, okHttpClient, gson, this::sessionId, this::eventAfterEnd, this::onProofEventEnded);
		board.start();
		proofs.start();
		overlay = new EventOverlay(config, this);
		overlayManager.add(overlay);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "sidebar.png");
		panel = new EventPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("Nylocas Clan Events")
			.icon(icon)
			.priority(10)
			.panel(panel)
			.build();
		syncSidebar();
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
			SwingUtilities.invokeLater(this::onLogout);
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
			SwingUtilities.invokeLater(() ->
			{
				refreshPanelKey();
				syncSidebar();
			});
			return;
		}
		if (NylocasConfig.CONNECT.equals(event.getKey()))
		{
			SwingUtilities.invokeLater(() ->
			{
				if (!config.connect())
				{
					refreshPanelKey();
				}
				syncSidebar();
			});
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

	private boolean sidebarVisible()
	{
		return config.connect() && !teamKey().isEmpty();
	}

	private void syncSidebar()
	{
		NavigationButton button = navButton;
		if (!running || button == null)
		{
			return;
		}
		if (sidebarVisible())
		{
			clientToolbar.addNavigation(button);
			return;
		}
		clientToolbar.removeNavigation(button);
	}

	private void refreshSessionButton()
	{
		EventPanel current = panel;
		if (running && current != null)
		{
			current.onWebsocketChanged();
		}
	}

	private void onLogout()
	{
		EventPanel current = panel;
		if (running && current != null)
		{
			current.loggedOut();
		}
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
		if (drops != null)
		{
			drops.clear();
		}
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

	private EventWebsocket.EnemyFilter enemyFilters()
	{
		return new EventWebsocket.EnemyFilter(config.receiveEnemy(), config.receiveEnemyRejections(), config.enemyEventDrop());
	}

	private void pushEnemyFilters()
	{
		if (!running || websocket == null)
		{
			return;
		}
		websocket.pushEnemyFilters(enemyFilters());
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
		websocket.open(teamKey(), event.startsAt, event.endsAt);
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
			if (current == null || !current.eventEnded())
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

	private void onLogin()
	{
		if (!running || !sidebarVisible())
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
		if (line == null || Text.removeTags(line).trim().isEmpty())
		{
			return;
		}
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
		if (!TileMatch.allowsSource(tiles, sourceName))
		{
			return;
		}
		List<String> seen = new ArrayList<>();
		List<String> labels = new ArrayList<>();
		List<LootDrop> matched = new ArrayList<>();
		for (ItemStack stack : lootReceived.getItems())
		{
			String label = itemLabel(stack.getId());
			seen.add(label);
			TileMatch.ProofTarget target = TileMatch.firstProof(tiles, sourceName, stack.getId());
			if (target == null)
			{
				continue;
			}
			target.itemName = label;
			if (!labels.contains(label))
			{
				labels.add(label);
			}
			matched.add(new LootDrop(stack.getId(), label, target));
		}
		log.debug("Allowed source {} dropped {}", sourceName, seen);
		if (matched.isEmpty())
		{
			return;
		}
		boolean saveLocally = config.saveProofs();
		boolean live = config.connect() && !proofsStopped && !board.beforeStart() && !board.afterEnd();
		boolean uploadProofs = config.submitProofs() && live;
		List<TileMatch.ProofTarget> targets = List.of();
		String proofKey = "";
		long proofPlayerId = 0;
		if (live)
		{
			proofKey = teamKey();
			proofPlayerId = EventPlayer.playerId(board.players(), localPlayerName());
		}
		if (uploadProofs)
		{
			// Reserving one drop changes which tile the next drop matches.
			targets = reserveProofs(tiles, sourceName, matched);
			if (proofKey.isEmpty() || proofPlayerId == 0)
			{
				proofs.releaseReservations(targets);
				proofs.proofFailed();
				targets = List.of();
				uploadProofs = false;
			}
		}
		if (!board.beforeStart() && config.eventDrop())
		{
			List<TileMatch.ProofTarget> chatTargets = uploadProofs ? targets : dropsOf(matched);
			String template = uploadProofs ? NotificationText.OWN_DROP_SUBMITTED : NotificationText.OWN_DROP;
			for (TileMatch.ProofTarget target : chatTargets)
			{
				String dropName = target.itemName == null ? "" : target.itemName;
				chat(NotificationText.drop(template, teamName(), dropName, TileText.tileTitle(target.tile), config.ownTeamColor(), config.dropColor(), config.tileColor()));
			}
		}
		if (live && !proofKey.isEmpty() && proofPlayerId != 0)
		{
			List<TileMatch.ProofTarget> announced = uploadProofs ? targets : dropsOf(matched);
			for (TileMatch.ProofTarget target : announced)
			{
				drops.announce(target, proofPlayerId, proofKey);
			}
		}
		if (!saveLocally && !uploadProofs)
		{
			return;
		}
		proofs.capture(this::pluginDirectory, board.eventName(), labels, saveLocally, uploadProofs, targets, proofPlayerId, proofKey, completionCount(sourceName), config.hidePrivateMessages(), config.openChat(), discordNotice());
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

	private List<TileMatch.ProofTarget> reserveProofs(List<EventTile> tiles, String sourceName, List<LootDrop> matched)
	{
		List<TileMatch.ProofTarget> targets = new ArrayList<>();
		for (LootDrop drop : matched)
		{
			TileMatch.ProofTarget target = TileMatch.firstProof(tiles, sourceName, drop.itemId);
			if (target == null)
			{
				continue;
			}
			target.item.pending++;
			target.itemName = drop.label;
			targets.add(target);
		}
		return targets;
	}

	private static List<TileMatch.ProofTarget> dropsOf(List<LootDrop> matched)
	{
		List<TileMatch.ProofTarget> targets = new ArrayList<>(matched.size());
		for (LootDrop drop : matched)
		{
			targets.add(drop.target);
		}
		return targets;
	}

	private ProofCapture.DiscordNotice discordNotice()
	{
		if (!config.sendDiscord())
		{
			return null;
		}
		String webhook = board.discordWebhookUrl();
		if (webhook == null || webhook.isEmpty())
		{
			return null;
		}
		String player = localPlayerName();
		return new ProofCapture.DiscordNotice(webhook, player, board.teamName());
	}

	private String localPlayerName()
	{
		if (client.getLocalPlayer() == null)
		{
			return null;
		}
		return client.getLocalPlayer().getName();
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

	private static final class LootDrop
	{
		final int itemId;
		final String label;
		final TileMatch.ProofTarget target;

		LootDrop(int itemId, String label, TileMatch.ProofTarget target)
		{
			this.itemId = itemId;
			this.label = label;
			this.target = target;
		}
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
