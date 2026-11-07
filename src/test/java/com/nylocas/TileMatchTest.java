package com.nylocas;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TileMatchTest
{
	@Test
	public void boardOrder()
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

		TileMatch.ProofTarget target = TileMatch.firstProof(List.of(later, earlier), "Crystalline Hunllef", 4207);

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

		TileMatch.ProofTarget target = TileMatch.firstProof(List.of(full, next), "Corrupted Hunllef", 4207);

		assertEquals(9, target.tile.id);
		assertEquals("the_corrupted_gauntlet", target.sourceId);
	}

	@Test
	public void reservedSlot()
	{
		EventTile first = seedTile();
		EventTile second = seedTile();
		second.id = 9;
		second.col = 1;
		List<EventTile> tiles = List.of(first, second);

		TileMatch.ProofTarget target = TileMatch.firstProof(tiles, "Crystalline Hunllef", 4207);
		target.item.pending++;

		assertEquals(9, TileMatch.firstProof(tiles, "Crystalline Hunllef", 4207).tile.id);
	}

	@Test
	public void localReservationFillsTheSlot()
	{
		EventTile tile = seedTile();
		EventBoardClient.hold(tile.items.get(0));

		assertNull(TileMatch.firstProof(List.of(tile), "Crystalline Hunllef", 4207));
	}

	@Test
	public void poolPending()
	{
		EventTile tile = seedTile();
		tile.pool = true;
		tile.need = 2;
		tile.items.get(0).count = 1;
		tile.items.get(0).pending = 2;

		assertNull(TileMatch.firstProof(List.of(tile), "Crystalline Hunllef", 4207));
	}

	@Test
	public void skipsAFinishedTile()
	{
		EventTile done = seedTile();
		done.complete = true;
		EventTile next = seedTile();
		next.id = 9;
		next.col = 1;

		assertEquals(9, TileMatch.firstProof(List.of(done, next), "Crystalline Hunllef", 4207).tile.id);
	}

	@Test
	public void wrongItemIsNotAProof()
	{
		assertNull(TileMatch.firstProof(List.of(seedTile()), "Crystalline Hunllef", 4151));
	}

	@Test
	public void allowedSource()
	{
		List<EventTile> tiles = List.of(seedTile());

		assertTrue(TileMatch.allowsSource(tiles, "Crystalline Hunllef"));
		assertTrue(TileMatch.allowsSource(tiles, "Corrupted Hunllef"));
		assertFalse(TileMatch.allowsSource(tiles, "The Gauntlet"));
		assertFalse(TileMatch.allowsSource(tiles, "Goblin"));
		assertFalse(TileMatch.allowsSource(tiles, null));
		assertFalse(TileMatch.allowsSource(null, "Crystalline Hunllef"));
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
