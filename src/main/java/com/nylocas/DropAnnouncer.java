package com.nylocas;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
final class DropAnnouncer
{
	private static final int CAP = 32;
	private static final MediaType JSON = MediaType.parse("application/json");

	private final OkHttpClient http;
	private final Gson gson;
	private final LongSupplier ids;
	private final ArrayDeque<Long> announced = new ArrayDeque<>();

	DropAnnouncer(OkHttpClient http, Gson gson)
	{
		this(http, gson, () -> ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE));
	}

	DropAnnouncer(OkHttpClient http, Gson gson, LongSupplier ids)
	{
		this.http = http;
		this.gson = gson;
		this.ids = ids;
	}

	long announce(AllowedDrops.ProofTarget target, long playerId, String key)
	{
		if (target == null || target.tile == null || target.item == null || target.item.key == null || target.item.key.isEmpty() || target.sourceId == null || target.sourceId.isEmpty())
		{
			return 0;
		}
		long id = ids.getAsLong();
		remember(id);
		Body body = new Body();
		body.tileId = target.tile.id;
		body.playerId = playerId;
		body.item = target.item.key;
		body.source = target.sourceId;
		body.id = id;
		Request call = new Request.Builder()
			.url(NylocasUrls.pluginDrop())
			.header("Authorization", "Bearer " + key)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();
		http.newCall(call).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call ignored, IOException error)
			{
				log.debug("Drop announce failed", error);
				forget(id);
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
					forget(id);
				}
				finally
				{
					response.close();
				}
			}
		});
		return id;
	}

	boolean consume(long id)
	{
		if (id <= 0)
		{
			return false;
		}
		synchronized (announced)
		{
			return announced.remove(id);
		}
	}

	void clear()
	{
		synchronized (announced)
		{
			announced.clear();
		}
	}

	private void remember(long id)
	{
		synchronized (announced)
		{
			announced.addLast(id);
			while (announced.size() > CAP)
			{
				announced.removeFirst();
			}
		}
	}

	private void forget(long id)
	{
		synchronized (announced)
		{
			announced.remove(id);
		}
	}

	private static final class Body
	{
		@SerializedName("tile_id")
		long tileId;

		@SerializedName("player_id")
		long playerId;

		String item;
		String source;
		long id;
	}
}
