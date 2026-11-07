package com.nylocas;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Filepath;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
final class ProofCapture
{
	private static final DateTimeFormatter SHOT_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS").withZone(ZoneId.systemDefault());
	// Game-chat tab written to VarClientID.CHAT_VIEW. The API names the varc, not this value.
	private static final int CHAT_VIEW_GAME = 1;
	// proc,redraw_chat_buttons. Not exported by ScriptID or gameval.
	private static final int REDRAW_CHAT_BUTTONS = 178;
	// proc,chat_alert_set. Not exported by ScriptID or gameval.
	private static final int CHAT_ALERT_SET = 183;
	// proc,toplevel_chatbox_background. Not exported by ScriptID or gameval.
	private static final int TOPLEVEL_CHATBOX_BACKGROUND = 923;

	private final Client client;
	private final ClientThread clientThread;
	private final DrawManager drawManager;
	private final ScheduledExecutorService executor;
	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final IntSupplier sessionId;
	private final BooleanSupplier eventAfterEnd;
	private final IntConsumer onEventEnded;
	private final Holds holds;
	private final List<Call> proofCalls = new ArrayList<>();
	private volatile boolean running;
	private int hiddenPrivateFrames;
	private int openChatFrames;
	private int savedChatView = -1;

	ProofCapture(Client client, ClientThread clientThread, DrawManager drawManager, ScheduledExecutorService executor, OkHttpClient okHttpClient, Gson gson, IntSupplier sessionId, BooleanSupplier eventAfterEnd, IntConsumer onEventEnded, Holds holds)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.drawManager = drawManager;
		this.executor = executor;
		this.okHttpClient = okHttpClient;
		this.gson = gson;
		this.sessionId = sessionId;
		this.eventAfterEnd = eventAfterEnd;
		this.onEventEnded = onEventEnded;
		this.holds = holds;
	}

	void start()
	{
		running = true;
	}

	void stop()
	{
		running = false;
		cancelProofCalls();
	}

	void proofFailed()
	{
		postGameMessage(ColorUtil.wrapWithColorTag(ProofErrors.MANUAL, Color.RED));
	}

	void cancelUploads()
	{
		cancelProofCalls();
	}

	void releaseReservations(List<TileMatch.ProofTarget> targets)
	{
		for (TileMatch.ProofTarget target : targets)
		{
			releaseReservation(target);
		}
	}

	void capture(Directory directory, String eventName, List<String> labels, boolean saveLocally, boolean uploadProofs, List<TileMatch.ProofTarget> proofs, long playerId, String proofKey, Integer completions, boolean hidePrivateMessages, boolean openChat, DiscordNotice discord, List<TileMatch.ProofTarget> discordDrops)
	{
		if (openChat)
		{
			showGameChat();
		}
		if (hidePrivateMessages)
		{
			hidePrivateChat();
		}
		List<TileMatch.ProofTarget> drops = discordDrops == null ? List.of() : discordDrops;
		drawManager.requestNextFrameListener(image ->
		{
			try
			{
				BufferedImage shot = copyFrame(image);
				executor.execute(() -> saveAndUploadScreenshot(directory, shot, eventName, labels, saveLocally, uploadProofs, proofs, playerId, proofKey, completions, discord, drops));
			}
			finally
			{
				showPrivateChat();
				restoreChat();
			}
		});
	}

	private void showGameChat()
	{
		if (chatIsBusy())
		{
			return;
		}
		if (openChatFrames == 0)
		{
			int view = client.getVarcIntValue(VarClientID.CHAT_VIEW);
			savedChatView = view == CHAT_VIEW_GAME ? -1 : view;
			if (view != CHAT_VIEW_GAME)
			{
				applyChatView(CHAT_VIEW_GAME);
			}
		}
		openChatFrames++;
	}

	private void restoreChat()
	{
		clientThread.invoke(() ->
		{
			if (openChatFrames <= 0)
			{
				return;
			}
			openChatFrames--;
			if (openChatFrames > 0 || savedChatView < 0)
			{
				return;
			}
			int view = savedChatView;
			savedChatView = -1;
			applyChatView(view);
		});
	}

	private void applyChatView(int view)
	{
		client.setVarcIntValue(VarClientID.CHAT_VIEW, view);
		if (view == CHAT_VIEW_GAME)
		{
			client.runScript(CHAT_ALERT_SET, CHAT_VIEW_GAME, 0);
		}
		client.runScript(TOPLEVEL_CHATBOX_BACKGROUND);
		client.runScript(REDRAW_CHAT_BUTTONS);
		client.runScript(ScriptID.SPLITPM_CHANGED);
	}

	private boolean chatIsBusy()
	{
		if (client.getWidget(InterfaceID.Chatmenu.OPTIONS) != null)
		{
			return true;
		}
		Widget layer = client.getWidget(InterfaceID.Chatbox.MES_LAYER);
		return layer != null && !layer.isSelfHidden();
	}

	private void hidePrivateChat()
	{
		Widget widget = client.getWidget(InterfaceID.PmChat.CONTAINER);
		if (widget == null || (widget.isHidden() && hiddenPrivateFrames == 0))
		{
			return;
		}
		if (!widget.isHidden())
		{
			widget.setHidden(true);
		}
		hiddenPrivateFrames++;
	}

	private void showPrivateChat()
	{
		clientThread.invoke(() ->
		{
			if (hiddenPrivateFrames <= 0)
			{
				return;
			}
			hiddenPrivateFrames--;
			if (hiddenPrivateFrames > 0)
			{
				return;
			}
			Widget widget = client.getWidget(InterfaceID.PmChat.CONTAINER);
			if (widget != null)
			{
				widget.setHidden(false);
			}
		});
	}

	private void saveAndUploadScreenshot(Directory directory, BufferedImage shot, String eventName, List<String> labels, boolean saveLocally, boolean uploadProofs, List<TileMatch.ProofTarget> targets, long playerId, String key, Integer completions, DiscordNotice discord, List<TileMatch.ProofTarget> discordDrops)
	{
		boolean sendDiscord = discord != null && discordDrops != null && !discordDrops.isEmpty();
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
			else if (running && sendDiscord)
			{
				postGameMessage(ColorUtil.wrapWithColorTag("Could not send drop to Discord", Color.RED));
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
			else if (saveLocally)
			{
				postGameMessage(ColorUtil.wrapWithColorTag("Could not save screenshot", Color.RED));
			}
			else if (sendDiscord)
			{
				postGameMessage(ColorUtil.wrapWithColorTag("Could not send drop to Discord", Color.RED));
			}
			return;
		}
		if (saveLocally)
		{
			boolean saved = saveScreenshot(directory, png, eventName, labels);
			if (!saved && !uploadProofs)
			{
				postGameMessage(ColorUtil.wrapWithColorTag("Could not save screenshot", Color.RED));
			}
		}
		if (sendDiscord && running)
		{
			for (TileMatch.ProofTarget drop : discordDrops)
			{
				postDiscord(png, discord, drop);
			}
		}
		if (!uploadProofs)
		{
			return;
		}
		for (TileMatch.ProofTarget target : targets)
		{
			if (!running)
			{
				releaseReservation(target);
				continue;
			}
			enqueueProof(png, target, playerId, key, completions);
		}
	}

	private boolean saveScreenshot(Directory directory, byte[] png, String eventName, List<String> labels)
	{
		try
		{
			String stamp = SHOT_STAMP.format(Instant.now());
			Filepath folder = directory.get().joinSegment(ScreenshotFiles.folderName(eventName));
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

	private void enqueueProof(byte[] png, TileMatch.ProofTarget target, long playerId, String key, Integer completions)
	{
		RequestBody file = RequestBody.create(MediaType.parse("image/png"), png);
		MultipartBody.Builder form = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("tile_id", Long.toString(target.tile.id))
			.addFormDataPart("player_id", Long.toString(playerId))
			.addFormDataPart("item", target.item.key)
			.addFormDataPart("source", target.sourceId);
		if (completions != null)
		{
			form.addFormDataPart("completions", Integer.toString(completions));
		}
		RequestBody body = form
			.addFormDataPart("file", "proof.png", file)
			.build();
		Request request = new Request.Builder()
			.url(NylocasUrls.pluginSubmission())
			.header("Authorization", "Bearer " + key)
			.post(body)
			.build();
		Call call = okHttpClient.newCall(request);
		int session = sessionId.getAsInt();
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
						acknowledgeReservation(target);
						return;
					}
					String responseBody = response.body() == null ? "" : response.body().string();
					log.debug("Proof rejected {} {}", response.code(), responseBody);
					releaseReservation(target);
					rejectProof(response.code(), responseBody, session);
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

	private void rejectProof(int status, String responseBody, int session)
	{
		String error = errorOf(responseBody);
		if (status == 401 && error.isEmpty())
		{
			error = "unauthorized";
		}
		if (ProofErrors.endsEvent(error, eventAfterEnd.getAsBoolean()))
		{
			onEventEnded.accept(session);
			return;
		}
		String line = ProofErrors.line(error);
		if (line == null)
		{
			return;
		}
		postGameMessage(ColorUtil.wrapWithColorTag(line, Color.RED));
	}

	private String errorOf(String body)
	{
		return ErrorBody.message(gson, body);
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

	private void releaseReservation(TileMatch.ProofTarget target)
	{
		if (target == null || target.tile == null || target.item == null || target.item.key == null)
		{
			return;
		}
		holds.release(target.tile.id, target.item.key);
	}

	private void acknowledgeReservation(TileMatch.ProofTarget target)
	{
		if (target == null || target.tile == null || target.item == null || target.item.key == null)
		{
			return;
		}
		holds.acknowledge(target.tile.id, target.item.key);
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

	interface Holds
	{
		void release(long tileId, String itemKey);

		void acknowledge(long tileId, String itemKey);
	}

	@FunctionalInterface
	interface Directory
	{
		Filepath get() throws IOException;
	}

	private void postDiscord(byte[] png, DiscordNotice notice, TileMatch.ProofTarget target)
	{
		String webhook = notice == null ? "" : DiscordWebhook.allowed(notice.webhook);
		if (webhook.isEmpty() || png == null)
		{
			return;
		}
		String item = target == null || target.itemName == null ? "" : target.itemName;
		String payload = discordPayload(gson, discordDropMessage(notice.teamName, notice.playerName, item, sourceLabel(target)));
		RequestBody body = new MultipartBody.Builder()
			.setType(MultipartBody.FORM)
			.addFormDataPart("payload_json", payload)
			.addFormDataPart("files[0]", "proof.png", RequestBody.create(MediaType.parse("image/png"), png))
			.build();
		Request request = new Request.Builder()
			.url(webhook)
			.post(body)
			.build();
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException error)
			{
				log.debug("Discord webhook failed", error);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response ignored = response)
				{
					if (!response.isSuccessful())
					{
						log.debug("Discord webhook rejected {}", response.code());
					}
				}
			}
		});
	}

	private static String discordDropMessage(String team, String player, String item, String source)
	{
		return "## " + team + "\n**" + player + "** has received: **" + item + "** from **" + source + "**";
	}

	private static String sourceLabel(TileMatch.ProofTarget target)
	{
		if (target == null || target.item == null || target.item.sources == null || target.sourceId == null)
		{
			return "";
		}
		for (EventSource source : target.item.sources)
		{
			if (source != null && target.sourceId.equals(source.id) && source.displayName != null && !source.displayName.isEmpty())
			{
				return source.displayName;
			}
		}
		return "";
	}

	static String discordPayload(Gson gson, String content)
	{
		DiscordPayload payload = new DiscordPayload();
		payload.content = content;
		return gson.toJson(payload);
	}

	static final class DiscordNotice
	{
		final String webhook;
		final String playerName;
		final String teamName;

		DiscordNotice(String webhook, String playerName, String teamName)
		{
			this.webhook = webhook;
			this.playerName = playerName == null ? "" : playerName;
			this.teamName = teamName == null ? "" : teamName;
		}
	}

	private static final class DiscordPayload
	{
		String content;

		@SerializedName("allowed_mentions")
		Mentions allowedMentions = new Mentions();

		private static final class Mentions
		{
			List<String> parse = List.of();
		}
	}
}
