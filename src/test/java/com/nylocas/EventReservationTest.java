package com.nylocas;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class EventReservationTest
{
	@Test
	public void progressLeavesAnUploadInFlight()
	{
		EventItem item = item();
		EventBoardClient.hold(item);
		List<EventTile> tiles = List.of(tile(item));

		EventBoardClient.applyServerProgress(tiles, 4, "crystal_weapon_seed", 0, 1, false);

		assertEquals(1, item.reserved);
		assertEquals(0, item.acked);
		assertEquals(1, item.pending);
	}

	@Test
	public void successfulUploadStaysReservedUntilTheServerCountsIt()
	{
		EventItem item = item();
		EventBoardClient.hold(item);
		List<EventTile> tiles = List.of(tile(item));
		EventBoardClient.acknowledgeHold(tiles, 4, "crystal_weapon_seed");

		assertEquals(1, item.reserved);
		assertEquals(1, item.acked);

		EventBoardClient.applyServerProgress(tiles, 4, "crystal_weapon_seed", 0, 0, false);
		assertEquals(1, item.reserved);

		EventBoardClient.applyServerProgress(tiles, 4, "crystal_weapon_seed", 0, 1, false);
		assertEquals(0, item.reserved);
		assertEquals(0, item.acked);
		assertEquals(1, item.pending);
	}

	@Test
	public void progressThatAlreadyLandedReleasesTheHoldOnSuccess()
	{
		EventItem item = item();
		EventBoardClient.hold(item);
		List<EventTile> tiles = List.of(tile(item));
		EventBoardClient.applyServerProgress(tiles, 4, "crystal_weapon_seed", 0, 1, false);
		EventBoardClient.acknowledgeHold(tiles, 4, "crystal_weapon_seed");

		assertEquals(0, item.reserved);
		assertEquals(1, item.pending);
	}

	@Test
	public void existingServerCountIsNotTreatedAsThisUpload()
	{
		EventItem item = item();
		item.pending = 1;
		EventBoardClient.hold(item);
		List<EventTile> tiles = List.of(tile(item));
		EventBoardClient.acknowledgeHold(tiles, 4, "crystal_weapon_seed");

		assertEquals(1, item.reserved);

		EventBoardClient.applyServerProgress(tiles, 4, "crystal_weapon_seed", 0, 2, false);
		assertEquals(0, item.reserved);
		assertEquals(2, item.pending);
	}

	@Test
	public void failedUploadReleasesTheInFlightHold()
	{
		EventItem item = item();
		EventBoardClient.hold(item);
		EventBoardClient.hold(item);
		List<EventTile> tiles = List.of(tile(item));

		EventBoardClient.releaseHold(tiles, 4, "crystal_weapon_seed");
		assertEquals(1, item.reserved);
		assertFalse(EventBoardClient.releaseHold(tiles, 9, "crystal_weapon_seed"));
	}

	@Test
	public void failedUploadDoesNotReleaseASuccessfulOne()
	{
		EventItem item = item();
		EventBoardClient.hold(item);
		EventBoardClient.hold(item);
		List<EventTile> tiles = List.of(tile(item));
		EventBoardClient.acknowledgeHold(tiles, 4, "crystal_weapon_seed");

		EventBoardClient.releaseHold(tiles, 4, "crystal_weapon_seed");

		assertEquals(1, item.reserved);
		assertEquals(1, item.acked);
	}

	@Test
	public void refreshKeepsAnUploadTheFetchedBoardDoesNotShow()
	{
		EventItem held = item();
		EventBoardClient.hold(held);
		EventItem fetched = item();
		List<EventTile> installed = EventBoardClient.copyTiles(List.of(tile(fetched)));

		EventBoardClient.carryReservations(List.of(tile(held)), installed);

		EventItem live = installed.get(0).items.get(0);
		assertEquals(0, fetched.reserved);
		assertEquals(1, live.reserved);
		assertEquals(0, live.pending);
	}

	@Test
	public void refreshDropsAHoldTheFetchedBoardAlreadyCounts()
	{
		EventItem held = item();
		EventBoardClient.hold(held);
		EventBoardClient.acknowledgeHold(List.of(tile(held)), 4, "crystal_weapon_seed");
		EventItem fetched = item();
		fetched.pending = 1;
		List<EventTile> installed = EventBoardClient.copyTiles(List.of(tile(fetched)));

		EventBoardClient.carryReservations(List.of(tile(held)), installed);

		assertEquals(0, installed.get(0).items.get(0).reserved);
		assertEquals(1, installed.get(0).items.get(0).pending);
	}

	private static EventItem item()
	{
		EventItem item = new EventItem();
		item.key = "crystal_weapon_seed";
		item.count = 2;
		return item;
	}

	private static EventTile tile(EventItem item)
	{
		EventTile tile = new EventTile();
		tile.id = 4;
		tile.items = List.of(item);
		return tile;
	}
}
