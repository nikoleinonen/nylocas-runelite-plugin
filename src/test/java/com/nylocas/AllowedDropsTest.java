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
	public void firstOpenTileInBoardOrder()
	{
		EventTile later = seedTile();
		later.id = 9;
		later.row = 0;
		later.col = 2;
		later.title = "Later";
		EventTile earlier = seedTile();
		earlier.id = 4;
		earlier.row = 0;
		earlier.col = 0;
		earlier.title = "Earlier";

		AllowedDrops.ProofTarget target = AllowedDrops.firstProof(List.of(later, earlier), "Crystalline Hunllef", 4207);

		assertEquals(4, target.tile.id);
		assertEquals("the_gauntlet", target.sourceId);
		assertEquals("crystal_weapon_seed", target.item.key);
	}

	@Test
	public void skipsAFullTile()
	{
		EventTile full = seedTile();
		full.items.get(0).pending = 1;
		EventTile next = seedTile();
		next.id = 9;
		next.col = 1;
		next.title = "Next";

		AllowedDrops.ProofTarget target = AllowedDrops.firstProof(List.of(full, next), "Corrupted Hunllef", 4207);

		assertEquals(9, target.tile.id);
		assertEquals("the_corrupted_gauntlet", target.sourceId);
	}

	@Test
	public void secondDropMovesOnOnceTheFirstSlotIsReserved()
	{
		EventTile first = seedTile();
		EventTile second = seedTile();
		second.id = 9;
		second.col = 1;
		List<EventTile> tiles = List.of(first, second);

		AllowedDrops.ProofTarget target = AllowedDrops.firstProof(tiles, "Crystalline Hunllef", 4207);
		target.item.pending++;

		assertEquals(9, AllowedDrops.firstProof(tiles, "Crystalline Hunllef", 4207).tile.id);
	}

	@Test
	public void poolCountsEveryPendingDrop()
	{
		EventTile tile = seedTile();
		tile.pool = true;
		tile.need = 2;
		tile.items.get(0).count = 1;
		tile.items.get(0).pending = 2;

		assertEquals(null, AllowedDrops.firstProof(List.of(tile), "Crystalline Hunllef", 4207));
	}

	@Test
	public void skipsAFinishedTile()
	{
		EventTile done = seedTile();
		done.complete = true;
		EventTile next = seedTile();
		next.id = 9;
		next.col = 1;

		assertEquals(9, AllowedDrops.firstProof(List.of(done, next), "Crystalline Hunllef", 4207).tile.id);
	}

	@Test
	public void wrongItemIsNotAProof()
	{
		assertEquals(null, AllowedDrops.firstProof(List.of(seedTile()), "Crystalline Hunllef", 4151));
	}

	@Test
	public void playerNameMatchesTheRoster()
	{
		EventPlayer weirdchimp = new EventPlayer();
		weirdchimp.id = 9;
		weirdchimp.name = "WeirdChimp";
		EventPlayer zezima = new EventPlayer();
		zezima.id = 4;
		zezima.name = "Zezima";
		List<EventPlayer> players = List.of(weirdchimp, zezima);

		assertEquals(9, AllowedDrops.playerId(players, "weirdchimp"));
		assertEquals(9, AllowedDrops.playerId(players, "<img=2>WeirdChimp"));
		assertEquals(9, AllowedDrops.playerId(players, "WeirdChimp\u00A0"));
		assertEquals(0, AllowedDrops.playerId(players, "Other"));
		assertEquals(0, AllowedDrops.playerId(players, null));
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
		corrupted.id = "the_corrupted_gauntlet";
		corrupted.displayName = "The Corrupted Gauntlet";
		corrupted.runeliteNames = List.of("Corrupted Hunllef");
		EventSource gauntlet = new EventSource();
		gauntlet.id = "the_gauntlet";
		gauntlet.displayName = "The Gauntlet";
		gauntlet.runeliteNames = List.of("Crystalline Hunllef");

		EventItem seed = new EventItem();
		seed.key = "crystal_weapon_seed";
		seed.count = 1;
		seed.runeliteIds = List.of(4207);
		seed.sources = List.of(corrupted, gauntlet);

		EventTile tile = new EventTile();
		tile.id = 333;
		tile.title = "Weapon and Armour seed";
		tile.need = 1;
		tile.items = List.of(seed);
		return tile;
	}
}
