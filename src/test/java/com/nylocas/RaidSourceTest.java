package com.nylocas;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RaidSourceTest
{
	@Test
	public void readsRaidCompletion()
	{
		assertEquals("Chambers of Xeric Challenge Mode", RaidSource.fromChat("Your completed Chambers of Xeric Challenge Mode count is: 12."));
		assertEquals("Theatre of Blood: Hard Mode", RaidSource.fromChat("Your completed Theatre of Blood: Hard Mode count is: 1,024."));
		assertEquals("Tombs of Amascut: Expert Mode", RaidSource.fromChat("Your completed Tombs of Amascut: Expert Mode count is: 3."));
		assertEquals("Tombs of Amascut: Entry Mode", RaidSource.fromChat("Your completed Tombs of Amascut: Entry Mode count is: 2."));
	}

	@Test
	public void ignoresOtherKillCounts()
	{
		assertNull(RaidSource.fromChat("Your Zulrah kill count is: 50."));
		assertNull(RaidSource.fromChat("Allowed drop: Armadyl piece"));
		assertNull(RaidSource.fromChat(null));
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
		String source = RaidSource.resolve("Chambers of Xeric", "Chambers of Xeric Challenge Mode");

		assertTrue(AllowedDrops.titles(List.of(normal), source, 20997).isEmpty());
		assertEquals(List.of("Olmlet"), AllowedDrops.titles(List.of(challenge), source, 20997));
	}

	@Test
	public void entryModeMatchesNeitherTombsSource()
	{
		EventTile normal = raidTile("Tombs of Amascut");
		EventTile expert = raidTile("Tombs of Amascut: Expert Mode");
		String source = RaidSource.resolve("Tombs of Amascut", "Tombs of Amascut: Entry Mode");

		assertTrue(AllowedDrops.titles(List.of(normal, expert), source, 27277).isEmpty());
	}

	private static EventTile raidTile(String lootName)
	{
		EventSource source = new EventSource();
		source.name = lootName;
		source.runeliteNames = List.of(lootName);
		EventItem item = new EventItem();
		item.item = "olmlet";
		item.runeliteIds = List.of(20997, 27277);
		item.sources = List.of(source);
		EventTile tile = new EventTile();
		tile.id = 1;
		tile.title = "Olmlet";
		tile.items = List.of(item);
		return tile;
	}
}
