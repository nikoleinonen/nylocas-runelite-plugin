package com.nylocas;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AllowedDropsTest
{
	@Test
	public void seedFromEitherGauntlet()
	{
		List<EventTile> tiles = List.of(seedTile());

		assertEquals(List.of("Weapon and Armour seed"), AllowedDrops.matchingTitles(tiles, "Crystalline Hunllef", 4207));
		assertEquals(List.of("Weapon and Armour seed"), AllowedDrops.matchingTitles(tiles, "Corrupted Hunllef", 4207));
		assertTrue(AllowedDrops.matchingTitles(tiles, "The Gauntlet", 4207).isEmpty());
	}

	@Test
	public void otherItemFromGauntlet()
	{
		assertTrue(AllowedDrops.matchingTitles(List.of(seedTile()), "Crystalline Hunllef", 4151).isEmpty());
	}

	@Test
	public void seedFromAnotherSource()
	{
		assertTrue(AllowedDrops.matchingTitles(List.of(seedTile()), "Goblin", 4207).isEmpty());
	}

	@Test
	public void anyKillFromAnAllowedSource()
	{
		List<EventTile> tiles = List.of(seedTile());

		assertTrue(AllowedDrops.allowsSource(tiles, "Crystalline Hunllef"));
		assertTrue(AllowedDrops.allowsSource(tiles, "Corrupted Hunllef"));
		assertFalse(AllowedDrops.allowsSource(tiles, "The Gauntlet"));
		assertFalse(AllowedDrops.allowsSource(tiles, "Goblin"));
		assertFalse(AllowedDrops.allowsSource(tiles, null));
		assertFalse(AllowedDrops.allowsSource(null, "Crystalline Hunllef"));
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
