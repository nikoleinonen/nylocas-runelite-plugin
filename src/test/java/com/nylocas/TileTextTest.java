package com.nylocas;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TileTextTest
{
	@Test
	public void namesTheItemAndSources()
	{
		EventItem seed = seedItem();

		assertEquals("Weapon and Armour seed", TileText.tileTitle(seedTile()));
		assertEquals("Crystal weapon seed", TileText.itemName(seed, id -> id == 4207 ? "Crystal weapon seed" : null));
		assertEquals("The Corrupted Gauntlet, The Gauntlet", TileText.sourcesText(seed));
	}

	@Test
	public void fallsBackToCatalogName()
	{
		assertEquals("Crystal weapon seed", TileText.itemName(seedItem(), id -> null));
	}

	@Test
	public void describesTileProgress()
	{
		EventItem weapon = item("crystal_weapon_seed", 1, 1, 0, "The Gauntlet");
		EventItem armour = item("crystal_armour_seed", 1, 0, 1, "The Gauntlet");
		EventTile both = tile(2, false, weapon, armour);

		assertEquals("All of these", TileText.requirement(both));
		assertEquals("1/2", TileText.progress(both));
		assertEquals("1/1", TileText.itemFraction(both, weapon));
		assertEquals("0/1", TileText.itemFraction(both, armour));
		assertTrue(TileText.itemDone(both, weapon));
		assertTrue(!TileText.itemDone(both, armour));
		assertEquals("1 pending", TileText.pendingText(armour));

		EventTile either = tile(1, false, weapon, armour);
		assertEquals("Any one of these", TileText.requirement(either));

		EventItem orb = item("volatile_orb", 1, 1, 0, "Phosani's Nightmare");
		EventItem other = item("harmonised_orb", 1, 0, 2, "The Nightmare");
		EventTile drops = tile(2, true, orb, other);
		assertEquals("2 drops from this list", TileText.requirement(drops));
		assertEquals("1/2", TileText.progress(drops));
		assertEquals("1", TileText.itemFraction(drops, orb));
		assertEquals("2 pending", TileText.pendingText(other));
	}

	private static EventTile tile(int need, boolean pool, EventItem... items)
	{
		EventTile tile = new EventTile();
		tile.need = need;
		tile.pool = pool;
		tile.items = List.of(items);
		return tile;
	}

	private static EventItem item(String key, int count, int approved, int pending, String displayName)
	{
		EventSource source = new EventSource();
		source.displayName = displayName;
		EventItem item = new EventItem();
		item.key = key;
		item.count = count;
		item.approved = approved;
		item.pending = pending;
		item.sources = List.of(source);
		return item;
	}

	private static EventItem seedItem()
	{
		return seedTile().items.get(0);
	}

	private static EventTile seedTile()
	{
		EventSource corrupted = new EventSource();
		corrupted.displayName = "The Corrupted Gauntlet";
		corrupted.runeliteNames = List.of("Corrupted Hunllef");
		EventSource gauntlet = new EventSource();
		gauntlet.displayName = "The Gauntlet";
		gauntlet.runeliteNames = List.of("Crystalline Hunllef");

		EventItem seed = new EventItem();
		seed.key = "crystal_weapon_seed";
		seed.runeliteIds = List.of(4207);
		seed.sources = List.of(corrupted, gauntlet);

		EventTile tile = new EventTile();
		tile.id = 333;
		tile.title = "Weapon and Armour seed";
		tile.items = List.of(seed);
		return tile;
	}
}
