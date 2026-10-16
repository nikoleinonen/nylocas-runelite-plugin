package com.nylocas;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RaidSourceTest
{
	@Test
	public void readsRaidCompletion()
	{
		assertEquals("Chambers of Xeric Challenge Mode", RaidSource.fromKillCount("Your completed Chambers of Xeric Challenge Mode count is: 12."));
		assertEquals("Theatre of Blood: Hard Mode", RaidSource.fromKillCount("Your completed Theatre of Blood: Hard Mode count is: 1,024."));
		assertEquals("Tombs of Amascut: Expert Mode", RaidSource.fromKillCount("Your completed Tombs of Amascut: Expert Mode count is: 3."));
		assertEquals("Tombs of Amascut: Entry Mode", RaidSource.fromKillCount("Your completed Tombs of Amascut: Entry Mode count is: 2."));
	}

	@Test
	public void ignoresOtherKillCounts()
	{
		assertNull(RaidSource.fromKillCount("Your Zulrah kill count is: 50."));
		assertNull(RaidSource.fromKillCount("Completed allowed source: General Graardor, item: Rune platebody"));
		assertNull(RaidSource.fromKillCount(null));
	}

	@Test
	public void chestNeedsTheChatName()
	{
		assertNull(RaidSource.resolve("Chambers of Xeric", null));
		assertEquals("Chambers of Xeric", RaidSource.resolve("Chambers of Xeric", "Chambers of Xeric"));
		assertEquals("Chambers of Xeric Challenge Mode", RaidSource.resolve("Chambers of Xeric", "Chambers of Xeric Challenge Mode"));
		assertEquals("Kree'arra", RaidSource.resolve("Kree'arra", "Chambers of Xeric"));
	}

	@Test
	public void challengeModeDoesNotMatchNormal()
	{
		EventTile normal = raidTile("Chambers of Xeric");
		EventTile challenge = raidTile("Chambers of Xeric Challenge Mode");
		challenge.id = 2;
		String source = RaidSource.resolve("Chambers of Xeric", "Chambers of Xeric Challenge Mode");

		assertNull(AllowedDrops.firstProof(List.of(normal), source, 20997));
		assertEquals(2, AllowedDrops.firstProof(List.of(challenge), source, 20997).tile.id);
	}

	@Test
	public void entryModeMatchesNeitherTombsSource()
	{
		EventTile normal = raidTile("Tombs of Amascut");
		EventTile expert = raidTile("Tombs of Amascut: Expert Mode");
		String source = RaidSource.resolve("Tombs of Amascut", "Tombs of Amascut: Entry Mode");

		assertNull(AllowedDrops.firstProof(List.of(normal, expert), source, 27277));
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
