package com.nylocas;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RaidLootTest
{
	@Test
	public void readsRaidCompletion()
	{
		assertEquals("Chambers of Xeric Challenge Mode", RaidLoot.fromKillCount("Your completed Chambers of Xeric Challenge Mode count is: 12."));
		assertEquals("Theatre of Blood: Hard Mode", RaidLoot.fromKillCount("Your completed Theatre of Blood: Hard Mode count is: 1,024."));
		assertEquals("Tombs of Amascut: Expert Mode", RaidLoot.fromKillCount("Your completed Tombs of Amascut: Expert Mode count is: 3."));
		assertEquals("Tombs of Amascut: Entry Mode", RaidLoot.fromKillCount("Your completed Tombs of Amascut: Entry Mode count is: 2."));
		assertEquals("Fractured Archive", RaidLoot.fromKillCount("Your completed Fractured Archive count is: 143."));
	}

	@Test
	public void ignoresOtherKillCounts()
	{
		assertNull(RaidLoot.fromKillCount("Your Zulrah kill count is: 50."));
		assertNull(RaidLoot.fromKillCount("Completed allowed source: General Graardor, item: Rune platebody"));
		assertNull(RaidLoot.fromKillCount("The Fractured Archive: Total completion time: 19:02.40. Personal best: 18:16.20"));
		assertNull(RaidLoot.fromKillCount(null));
	}

	@Test
	public void chestName()
	{
		assertNull(RaidLoot.resolve("Chambers of Xeric", null));
		assertEquals("Chambers of Xeric", RaidLoot.resolve("Chambers of Xeric", "Chambers of Xeric"));
		assertEquals("Chambers of Xeric Challenge Mode", RaidLoot.resolve("Chambers of Xeric", "Chambers of Xeric Challenge Mode"));
		assertEquals("Kree'arra", RaidLoot.resolve("Kree'arra", "Chambers of Xeric"));
		assertNull(RaidLoot.resolve("Fractured Archive", null));
		assertEquals("Fractured Archive", RaidLoot.resolve("Fractured Archive", "Fractured Archive"));
	}

	@Test
	public void challengeMode()
	{
		EventTile normal = raidTile("Chambers of Xeric");
		EventTile challenge = raidTile("Chambers of Xeric Challenge Mode");
		challenge.id = 2;
		String source = RaidLoot.resolve("Chambers of Xeric", "Chambers of Xeric Challenge Mode");

		assertNull(TileMatch.firstProof(List.of(normal), source, 20997));
		assertEquals(2, TileMatch.firstProof(List.of(challenge), source, 20997).tile.id);
	}

	@Test
	public void entryMode()
	{
		EventTile normal = raidTile("Tombs of Amascut");
		EventTile expert = raidTile("Tombs of Amascut: Expert Mode");
		String source = RaidLoot.resolve("Tombs of Amascut", "Tombs of Amascut: Entry Mode");

		assertNull(TileMatch.firstProof(List.of(normal, expert), source, 27277));
	}

	private static EventTile raidTile(String lootName)
	{
		EventSource source = new EventSource();
		source.id = lootName;
		source.displayName = lootName;
		source.runeliteNames = List.of(lootName);
		EventItem item = new EventItem();
		item.key = "olmlet";
		item.count = 1;
		item.runeliteIds = List.of(20997, 27277);
		item.sources = List.of(source);
		EventTile tile = new EventTile();
		tile.id = 1;
		tile.need = 1;
		tile.title = "Olmlet";
		tile.items = List.of(item);
		return tile;
	}
}
