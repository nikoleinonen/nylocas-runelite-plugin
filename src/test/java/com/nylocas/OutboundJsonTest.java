package com.nylocas;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OutboundJsonTest
{
	private final Gson gson = new Gson();

	@Test
	public void filterFrameUsesZeroAndOne()
	{
		JsonObject frame = gson.fromJson(EventWebsocket.filterText(gson, true, false, true), JsonObject.class);

		assertEquals("filter", frame.get("type").getAsString());
		assertEquals(1, frame.get("enemy").getAsInt());
		assertEquals(0, frame.get("enemy_reject").getAsInt());
		assertEquals(1, frame.get("enemy_drop").getAsInt());
	}

	@Test
	public void discordPayloadKeepsTheMessageAndDisablesMentions()
	{
		JsonObject payload = gson.fromJson(ProofCapture.discordPayload(gson, "Blue \"team\"\n**drop**"), JsonObject.class);

		assertEquals("Blue \"team\"\n**drop**", payload.get("content").getAsString());
		assertEquals(0, payload.getAsJsonObject("allowed_mentions").getAsJsonArray("parse").size());
	}
}
