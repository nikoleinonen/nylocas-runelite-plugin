package com.nylocas;

import java.awt.Color;
import net.runelite.client.util.ColorUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NotificationTextTest
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
		String own = ColorUtil.wrapWithColorTag("Blue", OWN);
		assertEquals("You got: " + drop + " for " + tile, NotificationText.drop(NotificationText.OWN_DROP, "Blue", "Bandos hilt", "Bandos", OWN, DROP, TILE));
		assertEquals("You got: " + drop + " for " + tile + ", auto submitted", NotificationText.drop(NotificationText.OWN_DROP_SUBMITTED, "Blue", "Bandos hilt", "Bandos", OWN, DROP, TILE));
		assertEquals(own + " found " + drop, NotificationText.drop("{team} found {drop}", "Blue", "Bandos hilt", "Bandos", OWN, DROP, TILE));
		String enemy = ColorUtil.wrapWithColorTag("Red", ENEMY);
		assertEquals(enemy + " got a drop: " + drop + " for " + tile, NotificationText.drop(NotificationText.TEAM_DROP, "Red", "Bandos hilt", "Bandos", ENEMY, DROP, TILE));
		assertEquals(own + " got a drop: " + drop + " for " + tile, NotificationText.drop(NotificationText.TEAM_DROP, "Blue", "Bandos hilt", "Bandos", OWN, DROP, TILE));
	}

	@Test
	public void writesApprovalLines()
	{
		String team = ColorUtil.wrapWithColorTag("Blue", OWN);
		String tile = ColorUtil.wrapWithColorTag("Bandos hilt", TILE);
		assertEquals(team + "'s submission for " + tile + " got approved", NotificationText.approved(NotificationText.APPROVED, "Blue", "Bandos hilt", OWN, TILE));
		assertEquals(team + "'s submission for " + tile + " got approved and that completes the tile: " + tile, NotificationText.approved(NotificationText.APPROVED_COMPLETE, "Blue", "Bandos hilt", OWN, TILE));
	}

	@Test
	public void writesReviewLines()
	{
		String team = ColorUtil.wrapWithColorTag("Blue", OWN);
		String tile = ColorUtil.wrapWithColorTag("Dragon warhammer", TILE);
		String reason = ColorUtil.wrapWithColorTag("Wrong boss", REASON);
		assertEquals(team + "'s submission for " + tile + " got rejected: " + reason, NotificationText.rejected(NotificationText.REJECTED_REASON, "Blue", "Dragon warhammer", "Wrong boss", OWN, TILE, REASON));
		assertEquals(team + "'s submission for " + tile + " got rejected", NotificationText.rejected(NotificationText.REJECTED, "Blue", "Dragon warhammer", "", OWN, TILE, REASON));
		assertEquals(team + "'s submission for " + tile + " got deleted", NotificationText.deleted(NotificationText.DELETED, "Blue", "Dragon warhammer", OWN, TILE));
	}

	@Test
	public void enemyColor()
	{
		String team = ColorUtil.wrapWithColorTag("Red", ENEMY);
		String tile = ColorUtil.wrapWithColorTag("Armadyl hilt", TILE);
		assertEquals(team + "'s submission for " + tile + " got approved", NotificationText.approved(NotificationText.APPROVED, "Red", "Armadyl hilt", ENEMY, TILE));
	}

	@Test
	public void writesRankLines()
	{
		String leader = ColorUtil.wrapWithColorTag("Blue", OWN);
		assertEquals(leader + " took the lead!", NotificationText.rank(NotificationText.RANK_LEAD, "Blue", OWN));
		assertEquals(leader + " won the event! Congratulations!", NotificationText.rank(NotificationText.RANK_WIN, "Blue", OWN));
		assertEquals("First place is tied.", NotificationText.rank(NotificationText.RANK_TIE, null, OWN));
		assertEquals("First place is tied.", NotificationText.rank(NotificationText.RANK_TIE, "", ENEMY));
	}

	@Test
	public void stripsTags()
	{
		String leader = ColorUtil.wrapWithColorTag("Blue", ENEMY);
		assertEquals(leader + " took the lead!", NotificationText.rank("<col=ff0000>{team}</col> took the lead!", "Blue</col><col=ffffff>", ENEMY));
		String named = ColorUtil.wrapWithColorTag("{tile}", OWN);
		String drop = ColorUtil.wrapWithColorTag("Bandos hilt", DROP);
		String tile = ColorUtil.wrapWithColorTag("Bandos", TILE);
		assertEquals(named + " got a drop: " + drop + " for " + tile, NotificationText.drop(NotificationText.TEAM_DROP, "{tile}", "Bandos hilt", "Bandos", OWN, DROP, TILE));
	}

	@Test
	public void blankTemplate()
	{
		assertEquals("", NotificationText.rank("  ", "Blue", OWN));
		assertEquals("", NotificationText.rank(null, "Blue", OWN));
		assertEquals("Still {nope}", NotificationText.drop("Still {nope}", "Blue", "Bandos hilt", "Bandos", OWN, DROP, TILE));
	}

	@Test
	public void filterFrame()
	{
		assertEquals(
			"{\"type\":\"filter\",\"enemy\":1,\"enemy_reject\":0,\"enemy_drop\":1}",
			EventWebsocket.filterText(true, false, true));
	}
}
