package com.nylocas;

import com.google.gson.Gson;
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
final class ProofUploader
{
	private static final DateTimeFormatter SHOT_STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss_SSS").withZone(ZoneId.systemDefault());

	private final Client client;
	private final ClientThread clientThread;
	private final DrawManager drawManager;
	private final ScheduledExecutorService executor;
	private final OkHttpClient okHttpClient;
	private final Gson gson;
	private final IntSupplier sessionId;
	private final BooleanSupplier eventAfterEnd;
	private final IntConsumer onEventEnded;
	private final List<Call> proofCalls = new ArrayList<>();
	private volatile boolean running;

	ProofUploader(Client client, ClientThread clientThread, DrawManager drawManager, ScheduledExecutorService executor, OkHttpClient okHttpClient, Gson gson, IntSupplier sessionId, BooleanSupplier eventAfterEnd, IntConsumer onEventEnded)
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
		postGameMessage(ColorUtil.wrapWithColorTag(ProofMessages.MANUAL, Color.RED));
	}

	void cancelUploads()
	{
		cancelProofCalls();
	}

	void releaseReservations(List<AllowedDrops.ProofTarget> targets)
	{
		for (AllowedDrops.ProofTarget target : targets)
		{
			releaseReservation(target);
		}
	}

	void capture(Directory directory, String eventName, List<String> labels, boolean saveLocally, boolean uploadProofs, List<AllowedDrops.ProofTarget> proofs, long playerId, String proofKey, Integer completions)
	{
		drawManager.requestNextFrameListener(image ->
		{
			BufferedImage shot = copyFrame(image);
			executor.execute(() -> saveAndUploadScreenshot(directory, shot, eventName, labels, saveLocally, uploadProofs, proofs, playerId, proofKey, completions));
		});
	}

	private void saveAndUploadScreenshot(Directory directory, BufferedImage shot, String eventName, List<String> labels, boolean saveLocally, boolean uploadProofs, List<AllowedDrops.ProofTarget> targets, long playerId, String key, Integer completions)
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
			boolean saved = saveScreenshot(directory, png, eventName, labels);
			if (!uploadProofs)
			{
				if (!saved)
				{
					postGameMessage(ColorUtil.wrapWithColorTag("Could not save screenshot", Color.RED));
				}
				return;
			}
		}
		if (!uploadProofs)
		{
			return;
		}
		for (AllowedDrops.ProofTarget target : targets)
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

	private void enqueueProof(byte[] png, AllowedDrops.ProofTarget target, long playerId, String key, Integer completions)
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
			.url(NylocasPlugin.API_BASE + "/plugin/submission")
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
		if (ProofMessages.endsEvent(error, eventAfterEnd.getAsBoolean()))
		{
			onEventEnded.accept(session);
			return;
		}
		String line = ProofMessages.line(error);
		if (line == null)
		{
			return;
		}
		postGameMessage(ColorUtil.wrapWithColorTag(line, Color.RED));
	}

	private String errorOf(String body)
	{
		try
		{
			ErrorBody parsed = gson.fromJson(body, ErrorBody.class);
			return parsed == null || parsed.error == null ? "" : parsed.error;
		}
		catch (RuntimeException ex)
		{
			return "";
		}
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

	@FunctionalInterface
	interface Directory
	{
		Filepath get() throws IOException;
	}

	private static final class ErrorBody
	{
		String error;
	}
}
