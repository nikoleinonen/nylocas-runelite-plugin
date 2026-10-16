package com.nylocas;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

public class EventTileCopyTest
{
	@Test
	public void reservationDoesNotChangeTheFetchedTile()
	{
		EventItem item = new EventItem();
		item.key = "crystal_weapon_seed";
		item.count = 1;
		item.pending = 1;
		item.runeliteIds = List.of(4207);

		EventTile fetched = new EventTile();
		fetched.id = 4;
		fetched.row = 1;
		fetched.col = 2;
		fetched.title = "Weapon seed";
		fetched.iconItem = "twisted_bow";
		fetched.imageUrl = "https://media.nylocas.com/tiles/abc.png";
		fetched.imageFit = "tile";
		fetched.iconCount = 3;
		fetched.need = 1;
		fetched.items = List.of(item);

		List<EventTile> loot = NylocasPlugin.copyTiles(List.of(fetched));
		EventItem reserved = loot.get(0).items.get(0);
		reserved.pending++;

		assertEquals(1, item.pending);
		assertEquals(2, reserved.pending);
		assertNotSame(fetched, loot.get(0));
		assertNotSame(item, reserved);
		assertEquals(4, loot.get(0).id);
		assertEquals(1, loot.get(0).row);
		assertEquals(2, loot.get(0).col);
		assertEquals("Weapon seed", loot.get(0).title);
		assertEquals("twisted_bow", loot.get(0).iconItem);
		assertEquals("https://media.nylocas.com/tiles/abc.png", loot.get(0).imageUrl);
		assertEquals("tile", loot.get(0).imageFit);
		assertEquals(Integer.valueOf(3), loot.get(0).iconCount);
		assertEquals(1, loot.get(0).need);
		assertEquals(List.of(4207), reserved.runeliteIds);
	}
}
