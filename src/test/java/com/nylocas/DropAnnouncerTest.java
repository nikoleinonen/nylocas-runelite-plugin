package com.nylocas;

import com.google.gson.Gson;
import java.util.concurrent.atomic.AtomicLong;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DropAnnouncerTest
{
	@Test
	public void skipsAnIncompleteTarget()
	{
		DropAnnouncer announcer = announcer(new AtomicLong(1));
		assertEquals(0, announcer.announce(null, 1, "key"));
		assertFalse(announcer.consume(1));
	}

	@Test
	public void consumeRemovesTheAnnouncedId()
	{
		AtomicLong ids = new AtomicLong(7);
		DropAnnouncer announcer = announcer(ids);
		assertEquals(7, announcer.announce(target(), 3, "key"));
		assertTrue(announcer.consume(7));
		assertFalse(announcer.consume(7));
	}

	@Test
	public void keepsTheLatestIds()
	{
		AtomicLong ids = new AtomicLong(1);
		DropAnnouncer announcer = announcer(ids);
		for (int i = 0; i < 32; i++)
		{
			announcer.announce(target(), 3, "key");
		}
		assertEquals(33, announcer.announce(target(), 3, "key"));
		assertFalse(announcer.consume(1));
		assertTrue(announcer.consume(2));
		assertTrue(announcer.consume(33));
	}

	private static DropAnnouncer announcer(AtomicLong ids)
	{
		OkHttpClient http = new OkHttpClient.Builder()
			.addInterceptor(chain -> new Response.Builder()
				.request(chain.request())
				.protocol(Protocol.HTTP_1_1)
				.code(200)
				.message("OK")
				.body(ResponseBody.create(MediaType.parse("application/json"), ""))
				.build())
			.build();
		return new DropAnnouncer(http, new Gson(), ids::getAndIncrement);
	}

	private static AllowedDrops.ProofTarget target()
	{
		EventTile tile = new EventTile();
		tile.id = 4;
		EventItem item = new EventItem();
		item.key = "bandos_hilt";
		return new AllowedDrops.ProofTarget(tile, item, "bandos");
	}
}
