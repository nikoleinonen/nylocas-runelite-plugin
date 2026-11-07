package com.nylocas;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

@Slf4j
final class EventWebsocket
{
	private static final int[] BACKOFF_SECONDS = {1, 2, 4, 8, 16, 32, 60};

	// Own-team progress and the current place update the board even when those chat messages are off.
	private static final boolean RANK = true;
	private static final boolean OWN = true;

	private final OkHttpClient http;
	private final Gson gson;
	private final ScheduledExecutorService executor;
	private final NylocasPlugin plugin;
	private final WebsocketFrames frames;
	private final Supplier<EnemyFilter> enemyFilters;

	private volatile int generation;
	private volatile boolean stopped = true;
	private int step;
	private String key = "";
	private boolean enemy;
	private boolean enemyReject;
	private boolean enemyDrop;
	private boolean filtersDirty;
	private Instant startsAt;
	private Instant endsAt;
	private ScheduledFuture<?> pending;
	private WebSocket socket;

	EventWebsocket(OkHttpClient http, Gson gson, ScheduledExecutorService executor, NylocasPlugin plugin, WebsocketFrames frames, Supplier<EnemyFilter> enemyFilters)
	{
		this.http = http.newBuilder()
			.pingInterval(60, TimeUnit.SECONDS)
			.readTimeout(150, TimeUnit.SECONDS)
			.build();
		this.gson = gson;
		this.executor = executor;
		this.plugin = plugin;
		this.frames = frames;
		this.enemyFilters = enemyFilters;
	}

	static String filterText(Gson gson, boolean enemy, boolean enemyReject, boolean enemyDrop)
	{
		FilterFrame frame = new FilterFrame();
		frame.enemy = enemy ? 1 : 0;
		frame.enemyReject = enemyReject ? 1 : 0;
		frame.enemyDrop = enemyDrop ? 1 : 0;
		return gson.toJson(frame);
	}

	void open(String teamKey, String startsAt, String endsAt)
	{
		stop();
		if (teamKey == null || teamKey.isEmpty())
		{
			return;
		}
		this.stopped = false;
		this.step = 0;
		this.key = teamKey;
		this.startsAt = EventTime.parse(startsAt);
		this.endsAt = EventTime.parse(endsAt);
		if (this.endsAt == null || !Instant.now().isBefore(this.endsAt))
		{
			if (this.endsAt != null)
			{
				finish(true);
			}
			else
			{
				stopped = true;
			}
			return;
		}
		if (this.startsAt != null && Instant.now().isBefore(this.startsAt))
		{
			schedule(this.startsAt.toEpochMilli() - Instant.now().toEpochMilli(), this::connectNow);
			return;
		}
		connectNow();
	}

	boolean isLive()
	{
		return !stopped;
	}

	void pushEnemyFilters(EnemyFilter next)
	{
		if (next == null)
		{
			return;
		}
		WebSocket current;
		synchronized (this)
		{
			boolean changed = this.enemy != next.enemy || this.enemyReject != next.enemyReject || this.enemyDrop != next.enemyDrop;
			this.enemy = next.enemy;
			this.enemyReject = next.enemyReject;
			this.enemyDrop = next.enemyDrop;
			if (!changed || stopped)
			{
				return;
			}
			current = socket;
			if (current == null)
			{
				filtersDirty = true;
				return;
			}
			filtersDirty = false;
		}
		current.send(filterText(gson, next.enemy, next.enemyReject, next.enemyDrop));
	}

	void stop()
	{
		WebSocket current;
		synchronized (this)
		{
			generation++;
			stopped = true;
			filtersDirty = false;
			cancelPending();
			current = socket;
			socket = null;
		}
		if (current != null)
		{
			current.close(1000, "");
		}
	}

	private void reconnect(int retryAfterSeconds)
	{
		if (stopped)
		{
			return;
		}
		if (endsAt == null || EventTime.afterEnd(endsAt, Instant.now()))
		{
			finish(EventTime.afterEnd(endsAt, Instant.now()));
			return;
		}
		if (startsAt != null && Instant.now().isBefore(startsAt))
		{
			schedule(startsAt.toEpochMilli() - Instant.now().toEpochMilli(), this::connectNow);
			return;
		}
		int backoff = BACKOFF_SECONDS[step];
		if (step < BACKOFF_SECONDS.length - 1)
		{
			step++;
		}
		long delay = Math.max(backoff, Math.max(0, retryAfterSeconds)) * 1000L;
		schedule(delay, this::connectNow);
	}

	private void connectNow()
	{
		if (stopped || endsAt == null || EventTime.afterEnd(endsAt, Instant.now()))
		{
			finish(!stopped && EventTime.afterEnd(endsAt, Instant.now()));
			return;
		}
		int ticket;
		String address;
		synchronized (this)
		{
			applyEnemyFilters(enemyFilters.get());
			filtersDirty = false;
			ticket = generation;
			address = NylocasUrls.websocket(NylocasUrls.hashKey(key), RANK, OWN, enemy, enemyReject, enemyDrop);
		}
		Request request = new Request.Builder().url(address).build();
		WebSocket opened = http.newWebSocket(request, new WebSocketListener()
		{
			@Override
			public void onOpen(WebSocket webSocket, Response response)
			{
				if (ticket != generation)
				{
					webSocket.close(1000, "");
					return;
				}
				step = 0;
			}

			@Override
			public void onMessage(WebSocket webSocket, String text)
			{
				if (ticket != generation)
				{
					return;
				}
				Frame frame;
				try
				{
					frame = gson.fromJson(text, Frame.class);
				}
				catch (RuntimeException ex)
				{
					log.debug("Websocket frame", ex);
					return;
				}
				if (frame == null || frame.type == null)
				{
					return;
				}
				SwingUtilities.invokeLater(() -> frames.onFrame(frame));
			}

			@Override
			public void onClosing(WebSocket webSocket, int code, String reason)
			{
				webSocket.close(code, reason);
			}

			@Override
			public void onClosed(WebSocket webSocket, int code, String reason)
			{
				if (ticket != generation)
				{
					return;
				}
				clearSocket(webSocket);
				if (stopped)
				{
					return;
				}
				if (code == 1000)
				{
					finish(EventTime.afterEnd(endsAt, Instant.now()));
					return;
				}
				reconnect(0);
			}

			@Override
			public void onFailure(WebSocket webSocket, Throwable error, Response response)
			{
				if (ticket != generation)
				{
					return;
				}
				clearSocket(webSocket);
				if (stopped)
				{
					return;
				}
				int status = response == null ? 0 : response.code();
				String message = errorText(response);
				if (status == 401)
				{
					finish(true);
					return;
				}
				if (status == 400 && "event has not started".equals(message))
				{
					reconnect(0);
					return;
				}
				if (!(error instanceof IOException))
				{
					log.debug("Websocket socket {}", status, error);
				}
				int retry = status == 429 || status == 503 ? retryAfter(response) : 0;
				reconnect(retry);
			}
		});
		synchronized (this)
		{
			if (ticket == generation)
			{
				socket = opened;
			}
		}
		if (ticket != generation)
		{
			opened.close(1000, "");
			return;
		}
		sendFiltersIfDirty();
	}

	private void clearSocket(WebSocket webSocket)
	{
		synchronized (this)
		{
			if (socket == webSocket)
			{
				socket = null;
			}
		}
	}

	private void applyEnemyFilters(EnemyFilter next)
	{
		if (next == null)
		{
			return;
		}
		enemy = next.enemy;
		enemyReject = next.enemyReject;
		enemyDrop = next.enemyDrop;
	}

	private void sendFiltersIfDirty()
	{
		WebSocket current;
		boolean enemy;
		boolean enemyReject;
		boolean enemyDrop;
		synchronized (this)
		{
			if (!filtersDirty || socket == null || stopped)
			{
				return;
			}
			filtersDirty = false;
			current = socket;
			enemy = this.enemy;
			enemyReject = this.enemyReject;
			enemyDrop = this.enemyDrop;
		}
		current.send(filterText(gson, enemy, enemyReject, enemyDrop));
	}

	private void finish(boolean eventOver)
	{
		if (stopped)
		{
			return;
		}
		stopped = true;
		cancelPending();
		SwingUtilities.invokeLater(() -> plugin.websocketEnded(eventOver));
	}

	private void schedule(long delayMillis, Runnable task)
	{
		cancelPending();
		int ticket = generation;
		pending = executor.schedule(() ->
		{
			if (ticket == generation && !stopped)
			{
				task.run();
			}
		}, Math.max(0, delayMillis), TimeUnit.MILLISECONDS);
	}

	private void cancelPending()
	{
		if (pending != null)
		{
			pending.cancel(false);
			pending = null;
		}
	}

	private String errorText(Response response)
	{
		if (response == null || response.body() == null)
		{
			return "";
		}
		try
		{
			ErrorBody body = gson.fromJson(response.body().string(), ErrorBody.class);
			return body == null || body.error == null ? "" : body.error;
		}
		catch (Exception ex)
		{
			return "";
		}
	}

	private static int retryAfter(Response response)
	{
		if (response == null)
		{
			return 60;
		}
		String raw = response.header("Retry-After");
		if (raw == null)
		{
			return 60;
		}
		try
		{
			int seconds = Integer.parseInt(raw.trim());
			if (seconds < 0 || seconds > 3600)
			{
				return 60;
			}
			return seconds;
		}
		catch (NumberFormatException ex)
		{
			return 60;
		}
	}

	static final class EnemyFilter
	{
		final boolean enemy;
		final boolean enemyReject;
		final boolean enemyDrop;

		EnemyFilter(boolean enemy, boolean enemyReject, boolean enemyDrop)
		{
			this.enemy = enemy;
			this.enemyReject = enemyReject;
			this.enemyDrop = enemyDrop;
		}
	}

	static final class Frame
	{
		String type;

		@SerializedName("team_name")
		String teamName;

		@SerializedName("tile_id")
		long tileId;

		long id;
		String title;
		String item;
		int approved;
		int pending;
		Boolean complete;
		boolean approval;
		String action;

		@SerializedName("rejection_reason")
		String rejectionReason;

		@SerializedName("open_row")
		Integer openRow;

		String leader;
		Boolean winner;
		String place;

		@SerializedName("event_code")
		String eventCode;
	}

	private static final class FilterFrame
	{
		String type = "filter";
		int enemy;

		@SerializedName("enemy_reject")
		int enemyReject;

		@SerializedName("enemy_drop")
		int enemyDrop;
	}

	private static final class ErrorBody
	{
		String error;
	}
}
