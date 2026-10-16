package com.nylocas;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.imageio.ImageIO;
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
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
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
	static final String API_BASE = "http://127.0.0.1:8080";
	private static final String TEAM_KEY_FILE = "team-key";
	private static final String PROOF_FAILED = "Failed to submit proof automatically. Please submit it manually.";
	private static final Color PROOF_SENT = new Color(0x10b981);

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
	private final List<Call> proofCalls = new ArrayList<>();
	private NavigationButton navButton;
	private volatile EventPanel panel;
	private Call fetchCall;
	private volatile boolean running;
	private static final DateTimeFormatter SHOT_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS").withZone(ZoneId.systemDefault());

	private volatile List<EventTile> tiles = List.of();
	private volatile List<EventPlayer> players = List.of();
	private volatile String eventName = "";
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
		if (fetchCall != null)
		{
			fetchCall.cancel();
			fetchCall = null;
		}
		cancelProofCalls();
		tiles = List.of();
		players = List.of();
		eventName = "";
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
		if (!config.connect())
		{
			panel.showStatus("Turn on Connect to nylocas.com in the plugin settings");
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
					tiles = copyTiles(next);
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
		List<EventTile> board = tiles;
		List<String> labels = dropLabels(board, sourceName, lootReceived.getItems());
		if (labels.isEmpty())
		{
			return;
		}
		boolean saveLocally = config.saveProofs();
		boolean uploadProofs = config.submitProofs() && config.connect();
		List<AllowedDrops.ProofTarget> proofs = List.of();
		String proofKey = "";
		long proofPlayerId = 0;
		if (uploadProofs)
		{
			proofs = proofTargets(board, sourceName, lootReceived.getItems());
			proofKey = teamKey();
			proofPlayerId = AllowedDrops.playerId(players, localPlayerName());
			if (proofKey.isEmpty() || proofPlayerId == 0)
			{
				releaseReservations(proofs);
				proofFailed();
				proofs = List.of();
				uploadProofs = false;
			}
		}
		if (!saveLocally && !uploadProofs)
		{
			return;
		}
		captureDrop(eventName, labels, saveLocally, uploadProofs, proofs, proofPlayerId, proofKey);
	}

	private void captureDrop(String eventName, List<String> labels, boolean saveLocally, boolean uploadProofs, List<AllowedDrops.ProofTarget> proofs, long playerId, String proofKey)
	{
		drawManager.requestNextFrameListener(image ->
		{
			BufferedImage shot = copyFrame(image);
			executor.execute(() -> saveAndUploadScreenshot(shot, eventName, labels, saveLocally, uploadProofs, proofs, playerId, proofKey));
		});
	}

	private String itemLabel(int itemId)
	{
		String label = itemName(itemId);
		return label == null ? Integer.toString(itemId) : label;
	}

	private List<String> dropLabels(List<EventTile> board, String sourceName, Collection<ItemStack> stacks)
	{
		List<String> labels = new ArrayList<>();
		for (ItemStack stack : stacks)
		{
			if (AllowedDrops.firstProof(board, sourceName, stack.getId()) == null)
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

	private List<AllowedDrops.ProofTarget> proofTargets(List<EventTile> board, String sourceName, Collection<ItemStack> stacks)
	{
		List<AllowedDrops.ProofTarget> targets = new ArrayList<>();
		for (ItemStack stack : stacks)
		{
			AllowedDrops.ProofTarget target = AllowedDrops.firstProof(board, sourceName, stack.getId());
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

	private void saveAndUploadScreenshot(BufferedImage shot, String eventName, List<String> labels, boolean saveLocally, boolean uploadProofs, List<AllowedDrops.ProofTarget> targets, long playerId, String key)
	{
		if (!running || shot == null)
		{
			if (uploadProofs)
			{
				releaseReservations(targets);
				if (running)
				{
					proofFailed();
				}
			}
			return;
		}
		byte[] png;
		try
		{
			png = writePng(shot);
		}
		catch (IOException ex)
		{
			log.warn("Could not encode proof", ex);
			if (uploadProofs)
			{
				releaseReservations(targets);
				proofFailed();
			}
			else
			{
				postGameMessage(ColorUtil.wrapWithColorTag("Could not save screenshot", Color.RED));
			}
			return;
		}
		if (saveLocally)
		{
			boolean saved = saveScreenshot(png, eventName, labels);
			if (!uploadProofs)
			{
				if (saved)
				{
					postGameMessage("Saved screenshot");
				}
				else
				{
					postGameMessage(ColorUtil.wrapWithColorTag("Could not save screenshot", Color.RED));
				}
				return;
			}
		}
		for (AllowedDrops.ProofTarget target : targets)
		{
			if (!running)
			{
				releaseReservation(target);
				continue;
			}
			enqueueProof(png, target, playerId, key);
		}
	}

	private boolean saveScreenshot(byte[] png, String eventName, List<String> labels)
	{
		try
		{
			String stamp = SHOT_STAMP.format(Instant.now());
			Filepath folder = getPluginDirectory().joinSegment(ScreenshotFiles.folderName(eventName));
			folder.createDirectories();
			String fileName = ScreenshotFiles.fileName(stamp, labels);
			Filepath file = folder.joinSegment(fileName);
			int suffix = 2;
			String stem = fileName.endsWith(".png") ? fileName.substring(0, fileName.length() - 4) : fileName;
			while (file.exists())
			{
				file = folder.joinSegment(stem + "-" + suffix + ".png");
				suffix++;
			}
			file.write(png);
			return true;
		}
		catch (IOException | RuntimeException ex)
		{
			log.warn("Could not save screenshot", ex);
			return false;
		}
	}

	private void enqueueProof(byte[] png, AllowedDrops.ProofTarget target, long playerId, String key)
	{
		RequestBody file = RequestBody.create(MediaType.parse("image/png"), png);
		RequestBody body = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("tile_id", Long.toString(target.tile.id))
			.addFormDataPart("player_id", Long.toString(playerId))
			.addFormDataPart("item", target.item.key)
			.addFormDataPart("source", target.sourceId)
			.addFormDataPart("file", "proof.png", file)
			.build();
		Request request = new Request.Builder()
			.url(API_BASE + "/plugin/proof")
			.header("Authorization", "Bearer " + key)
			.post(body)
			.build();
		Call call = okHttpClient.newCall(request);
		synchronized (proofCalls)
		{
			if (!running)
			{
				releaseReservation(target);
				return;
			}
			proofCalls.add(call);
		}
		call.enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException error)
			{
				untrackProof(call);
				if (call.isCanceled() || !running)
				{
					return;
				}
				log.debug("Proof upload failed", error);
				releaseReservation(target);
				proofFailed();
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				untrackProof(call);
				try (Response ignored = response)
				{
					if (!running)
					{
						return;
					}
					if (response.isSuccessful())
					{
						postGameMessage("Sent proof: " + ColorUtil.wrapWithColorTag(proofLabel(target), PROOF_SENT));
						return;
					}
					String responseBody = response.body() == null ? "" : response.body().string();
					log.debug("Proof rejected {} {}", response.code(), responseBody);
					releaseReservation(target);
					proofFailed();
				}
				catch (IOException ex)
				{
					log.debug("Proof upload failed", ex);
					if (!running)
					{
						return;
					}
					releaseReservation(target);
					proofFailed();
				}
			}
		});
	}

	private void untrackProof(Call call)
	{
		synchronized (proofCalls)
		{
			proofCalls.remove(call);
		}
	}

	private void cancelProofCalls()
	{
		List<Call> calls;
		synchronized (proofCalls)
		{
			calls = new ArrayList<>(proofCalls);
			proofCalls.clear();
		}
		for (Call call : calls)
		{
			call.cancel();
		}
	}

	private static String proofLabel(AllowedDrops.ProofTarget target)
	{
		if (target.tile.title != null && !target.tile.title.isEmpty())
		{
			return target.tile.title;
		}
		return target.itemName;
	}

	private void releaseReservations(List<AllowedDrops.ProofTarget> targets)
	{
		for (AllowedDrops.ProofTarget target : targets)
		{
			releaseReservation(target);
		}
	}

	private void releaseReservation(AllowedDrops.ProofTarget target)
	{
		Runnable undo = () ->
		{
			if (target.item.pending > 0)
			{
				target.item.pending--;
			}
		};
		if (client.isClientThread())
		{
			undo.run();
			return;
		}
		clientThread.invoke(undo);
	}

	private void proofFailed()
	{
		postGameMessage(ColorUtil.wrapWithColorTag(PROOF_FAILED, Color.RED));
	}

	private void postGameMessage(String message)
	{
		if (!running)
		{
			return;
		}
		clientThread.invoke(() ->
		{
			if (running)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
			}
		});
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

	private static BufferedImage copyFrame(Image image)
	{
		if (image == null)
		{
			return null;
		}
		int width = image.getWidth(null);
		int height = image.getHeight(null);
		if (width <= 0 || height <= 0)
		{
			return null;
		}
		BufferedImage copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = copy.createGraphics();
		graphics.drawImage(image, 0, 0, null);
		graphics.dispose();
		return copy;
	}

	private static byte[] writePng(BufferedImage image) throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		if (!ImageIO.write(image, "png", out))
		{
			throw new IOException("png writer missing");
		}
		return out.toByteArray();
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
