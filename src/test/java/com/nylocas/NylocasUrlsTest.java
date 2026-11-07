package com.nylocas;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NylocasUrlsTest
{
	@Test
	public void keyHash()
	{
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", NylocasUrls.hashKey("abc"));
	}

	@Test
	public void apiUrls()
	{
		String hash = NylocasUrls.hashKey("abc");
		assertEquals(NylocasUrls.API + "/plugin/event/" + hash, NylocasUrls.pluginEvent("abc"));
		assertEquals(NylocasUrls.API + "/plugin/event/" + hash + "?open_row=2", NylocasUrls.pluginEvent("abc", 2));
	}

	@Test
	public void websocketUrl()
	{
		assertEquals(
			NylocasUrls.WEBSOCKET + "/plugin/websocket/abc?rank=1&own=0&enemy=1&enemy_reject=0&enemy_drop=0",
			NylocasUrls.websocket("abc", true, false, true, false, false));
		assertEquals(
			NylocasUrls.WEBSOCKET + "/plugin/websocket/abc?rank=0&own=1&enemy=0&enemy_reject=1&enemy_drop=1",
			NylocasUrls.websocket("abc", false, true, false, true, true));
	}

	@Test
	public void eventUrl()
	{
		assertEquals(NylocasUrls.WEBSITE + "/", NylocasUrls.event("", 0));
		assertEquals(NylocasUrls.WEBSITE + "/", NylocasUrls.event(null, 54));
		assertEquals(NylocasUrls.WEBSITE + "/events/classic-bingo/54/overview", NylocasUrls.event("classic_bingo", 54));
		assertEquals(NylocasUrls.WEBSITE + "/events/levels-bingo/54/overview", NylocasUrls.event("levels_bingo", 54));
		assertEquals(NylocasUrls.WEBSITE + "/", NylocasUrls.event("other", 54));
	}
}
