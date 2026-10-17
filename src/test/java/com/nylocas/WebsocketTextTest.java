package com.nylocas;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WebsocketTextTest
{
	@Test
	public void writesApprovalLines()
	{
		assertEquals("Blue's drop Bandos hilt got approved", WebsocketText.approved("Blue", "Bandos hilt", false, "Bandos hilt"));
		assertEquals("Blue's drop Bandos hilt got approved and that completed Bandos hilt", WebsocketText.approved("Blue", "Bandos hilt", true, "Bandos hilt"));
	}

	@Test
	public void writesRankLines()
	{
		assertEquals("Blue took the lead!", WebsocketText.rank("Blue", false));
		assertEquals("Blue won the event! Congratulations!", WebsocketText.rank("Blue", true));
		assertEquals("First place is tied.", WebsocketText.rank(null, false));
	}

	@Test
	public void buildsTheLiveUrl()
	{
		assertEquals(
			"ws://127.0.0.1:8090/plugin/websocket/abc?rank=1&own=0&enemy=1",
			EventWebsocket.url("http://127.0.0.1:8080", "abc", true, false, true));
		assertEquals(
			"wss://ws.nylocas.com/plugin/websocket/abc?rank=0&own=1&enemy=0",
			EventWebsocket.url("https://api.nylocas.com", "abc", false, true, false));
	}
}
