package com.nylocas;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AllowedDropsTest
{
	@Test
	public void seedFromEitherGauntlet()
	{
		List<EventTile> tiles = List.of(seedTile());

		assertEquals(List.of("Weapon and Armour seed"), AllowedDrops.titles(tiles, "Crystalline Hunllef", 4207));
		assertEquals(List.of("Weapon and Armour seed"), AllowedDrops.titles(tiles, "Corrupted Hunllef", 4207));
		assertTrue(AllowedDrops.titles(tiles, "The Gauntlet", 4207).isEmpty());
	}

	@Test
	public void otherItemFromGauntlet()
	{
		assertTrue(AllowedDrops.titles(List.of(seedTile()), "Crystalline Hunllef", 4151).isEmpty());
	}

	@Test
	public void seedFromAnotherSource()
	{
		assertTrue(AllowedDrops.titles(List.of(seedTile()), "Goblin", 4207).isEmpty());
	}

	@Test
	public void listsItemAndSources()
	{
		List<ListedDrop> lines = AllowedDrops.describe(List.of(seedTile()), id -> id == 4207 ? "Crystal weapon seed" : null);

		assertEquals(1, lines.size());
		assertEquals("Weapon and Armour seed", lines.get(0).tile);
		assertEquals("Crystal weapon seed", lines.get(0).item);
		assertEquals("The Corrupted Gauntlet, The Gauntlet", lines.get(0).sources);
	}

	@Test
	public void fallsBackToCatalogName()
	{
		List<ListedDrop> lines = AllowedDrops.describe(List.of(seedTile()), id -> null);

		assertEquals("Crystal weapon seed", lines.get(0).item);
	}

	@Test
	public void describesTileProgress()
	{
		EventItem weapon = item("crystal_weapon_seed", 1, 1, 0, "The Gauntlet");
		EventItem armour = item("crystal_armour_seed", 1, 0, 1, "The Gauntlet");
		EventTile both = tile(2, false, weapon, armour);

		assertEquals("All of these", AllowedDrops.requirement(both));
		assertEquals("1/2", AllowedDrops.progress(both));
		assertEquals("1/1", AllowedDrops.itemFraction(both, weapon));
		assertEquals("0/1", AllowedDrops.itemFraction(both, armour));
		assertTrue(AllowedDrops.rowDone(both, weapon));
		assertTrue(!AllowedDrops.rowDone(both, armour));
		assertEquals("1 pending", AllowedDrops.pendingText(armour));

		EventTile either = tile(1, false, weapon, armour);
		assertEquals("Any one of these", AllowedDrops.requirement(either));

		EventItem orb = item("volatile_orb", 1, 1, 0, "Phosani's Nightmare");
		EventItem other = item("harmonised_orb", 1, 0, 2, "The Nightmare");
		EventTile drops = tile(2, true, orb, other);
		assertEquals("2 drops from this list", AllowedDrops.requirement(drops));
		assertEquals("1/2", AllowedDrops.progress(drops));
		assertEquals("1", AllowedDrops.itemFraction(drops, orb));
		assertEquals("2 pending", AllowedDrops.pendingText(other));
	}

	private static EventTile tile(int need, boolean pool, EventItem... items)
	{
		EventTile tile = new EventTile();
		tile.need = need;
		tile.pool = pool;
		tile.items = List.of(items);
		return tile;
	}

	private static EventItem item(String key, int count, int approved, int pending, String sourceName)
	{
		EventSource source = new EventSource();
		source.name = sourceName;
		EventItem item = new EventItem();
		item.item = key;
		item.count = count;
		item.approved = approved;
		item.pending = pending;
		item.sources = List.of(source);
		return item;
	}

	private static EventTile seedTile()
	{
		EventSource corrupted = new EventSource();
		corrupted.name = "The Corrupted Gauntlet";
		corrupted.runeliteNames = List.of("Corrupted Hunllef");
		EventSource gauntlet = new EventSource();
		gauntlet.name = "The Gauntlet";
		gauntlet.runeliteNames = List.of("Crystalline Hunllef");

		EventItem seed = new EventItem();
		seed.item = "crystal_weapon_seed";
		seed.runeliteIds = List.of(4207);
		seed.sources = List.of(corrupted, gauntlet);

		EventTile tile = new EventTile();
		tile.id = 333;
		tile.title = "Weapon and Armour seed";
		tile.items = List.of(seed);
		return tile;
	}
}
