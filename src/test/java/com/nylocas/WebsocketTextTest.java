package com.nylocas;

import java.awt.Color;
import net.runelite.client.util.ColorUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WebsocketTextTest
{
	private static final Color OWN = new Color(0x3DDC97);
	private static final Color ENEMY = new Color(0xFF6B6B);
	private static final Color DROP = new Color(0x10B981);
	private static final Color TILE = new Color(0x38BDF8);
	private static final Color REASON = new Color(0xC47A11);

	@Test
	public void writesEventDropLines()
	{
		String drop = ColorUtil.wrapWithColorTag("Bandos hilt", DROP);
		String tile = ColorUtil.wrapWithColorTag("Bandos", TILE);
		assertEquals("Event drop: " + drop + " for " + tile, WebsocketText.eventDrop("Bandos hilt", "Bandos", false, DROP, TILE));
		assertEquals("Event drop: " + drop + " for " + tile + ", auto submitted", WebsocketText.eventDrop("Bandos hilt", "Bandos", true, DROP, TILE));
	}

	@Test
	public void writesApprovalLines()
	{
		String team = ColorUtil.wrapWithColorTag("Blue", OWN);
		String tile = ColorUtil.wrapWithColorTag("Bandos hilt", TILE);
		assertEquals(team + "'s submission for " + tile + " got approved", WebsocketText.approved("Blue", "Bandos hilt", false, OWN, TILE));
		assertEquals(team + "'s submission for " + tile + " got approved and that completed the tile: " + tile, WebsocketText.approved("Blue", "Bandos hilt", true, OWN, TILE));
	}

	@Test
	public void writesReviewLines()
	{
		String team = ColorUtil.wrapWithColorTag("Blue", OWN);
		String tile = ColorUtil.wrapWithColorTag("Dragon warhammer", TILE);
		String reason = ColorUtil.wrapWithColorTag("Wrong boss", REASON);
		assertEquals(team + "'s submission for " + tile + " got rejected: " + reason, WebsocketText.rejected("Blue", "Dragon warhammer", "Wrong boss", OWN, TILE, REASON));
		assertEquals(team + "'s submission for " + tile + " got rejected", WebsocketText.rejected("Blue", "Dragon warhammer", "", OWN, TILE, REASON));
		assertEquals(team + "'s submission for " + tile + " got deleted", WebsocketText.deleted("Blue", "Dragon warhammer", OWN, TILE));
	}

	@Test
	public void colorsEnemyNamesSeparately()
	{
		String team = ColorUtil.wrapWithColorTag("Red", ENEMY);
		String tile = ColorUtil.wrapWithColorTag("Armadyl hilt", TILE);
		assertEquals(team + "'s submission for " + tile + " got approved", WebsocketText.approved("Red", "Armadyl hilt", false, ENEMY, TILE));
	}

	@Test
	public void writesRankLines()
	{
		String leader = ColorUtil.wrapWithColorTag("Blue", OWN);
		assertEquals(leader + " took the lead!", WebsocketText.rank("Blue", false, OWN));
		assertEquals(leader + " won the event! Congratulations!", WebsocketText.rank("Blue", true, OWN));
		assertEquals("First place is tied.", WebsocketText.rank(null, false, OWN));
		assertEquals("First place is tied.", WebsocketText.rank("", false, ENEMY));
	}

	@Test
	public void stripsTagsInsideAnAccent()
	{
		String leader = ColorUtil.wrapWithColorTag("Blue", ENEMY);
		assertEquals(leader + " took the lead!", WebsocketText.rank("Blue</col><col=ffffff>", false, ENEMY));
	}

	@Test
	public void buildsTheLiveUrl()
	{
		assertEquals(
			"ws://127.0.0.1:8090/plugin/websocket/abc?rank=1&own=0&enemy=1&enemy_reject=0",
			EventWebsocket.url("http://127.0.0.1:8080", "abc", true, false, true, false));
		assertEquals(
			"wss://ws.nylocas.com/plugin/websocket/abc?rank=0&own=1&enemy=0&enemy_reject=1",
			EventWebsocket.url("https://api.nylocas.com", "abc", false, true, false, true));
	}
}
