package com.nylocas;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventPlayerTest
{
	@Test
	public void playerName()
	{
		EventPlayer playerA = new EventPlayer();
		playerA.id = 9;
		playerA.name = "playerA";
		EventPlayer playerB = new EventPlayer();
		playerB.id = 4;
		playerB.name = "playerB";
		List<EventPlayer> players = List.of(playerA, playerB);

		assertEquals(9, EventPlayer.playerId(players, "playera"));
		assertEquals(9, EventPlayer.playerId(players, "<img=2>playerA"));
		assertEquals(9, EventPlayer.playerId(players, "playerA\u00A0"));
		assertEquals(4, EventPlayer.playerId(players, "playerB"));
		assertEquals(0, EventPlayer.playerId(players, "Other"));
		assertEquals(0, EventPlayer.playerId(players, null));
	}
}
