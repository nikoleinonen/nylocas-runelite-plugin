package com.nylocas;

import java.awt.Color;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TileIconsTest
{
	@Test
	public void usesTheCatalogIconWhenTheTileHasNoImage()
	{
		EventTile tile = tile();
		tile.iconItem = "Twisted Bow";
		tile.items = List.of(item("bandos_hilt", 11832));

		assertEquals("https://cdn.nylocas.com/resources/icons/items/twisted_bow.png", TileIcons.imageUrl(tile));
		assertEquals("https://cdn.nylocas.com/resources/icons/items/twisted_bow.png", TileIcons.catalogIconUrl(tile));
		assertFalse(TileIcons.covers(tile));
		assertEquals(Integer.valueOf(11832), TileIcons.runeLiteId(tile));
	}

	@Test
	public void usesTheFirstItemWhenTheIconIsEmpty()
	{
		EventTile tile = tile();
		tile.items = List.of(item("crystal_weapon_seed", 4207));

		assertEquals("https://cdn.nylocas.com/resources/icons/items/crystal_weapon_seed.png", TileIcons.imageUrl(tile));
		assertEquals(Integer.valueOf(4207), TileIcons.runeLiteId(tile));
	}

	@Test
	public void usesTheChosenIconAndFallsBackToTheFirstDrop()
	{
		EventTile tile = tile();
		tile.iconItem = "tzrek-jad";
		tile.items = List.of(item("fire_cape", 6570));

		assertEquals("https://cdn.nylocas.com/resources/icons/items/tzrek-jad.png", TileIcons.imageUrl(tile));
		assertEquals(Integer.valueOf(6570), TileIcons.runeLiteId(tile));
	}

	@Test
	public void coversACustomTileImage()
	{
		EventTile tile = tile();
		tile.imageUrl = "https://media.nylocas.com/tiles/abc_DEF-123.png";
		tile.imageFit = "tile";
		tile.iconCount = 5;
		tile.items = List.of(item("twisted_bow", 20997));

		assertTrue(TileIcons.covers(tile));
		assertEquals(tile.imageUrl, TileIcons.imageUrl(tile));
		assertNull(TileIcons.count(tile));
		assertEquals("https://cdn.nylocas.com/resources/icons/items/twisted_bow.png", TileIcons.catalogIconUrl(tile));
	}

	@Test
	public void centersACustomIconAndKeepsTheCount()
	{
		EventTile tile = tile();
		tile.imageUrl = "https://media.nylocas.com/tiles/abc.gif";
		tile.imageFit = "icon";
		tile.iconCount = 100500;

		assertTrue(TileIcons.customIcon(tile));
		assertFalse(TileIcons.covers(tile));
		assertEquals(tile.imageUrl, TileIcons.imageUrl(tile));
		assertEquals(Integer.valueOf(100500), TileIcons.count(tile));
		assertEquals("100K", TileIcons.formatCount(100500));
		assertEquals(Color.WHITE, TileIcons.countColor(100500));
	}

	@Test
	public void rejectsImagesOutsideTheCdn()
	{
		EventTile tile = tile();
		tile.imageUrl = "https://example.com/tiles/abc.png";
		tile.imageFit = "tile";
		tile.items = List.of(item("bones", 526));

		assertFalse(TileIcons.allowed(tile.imageUrl));
		assertEquals("https://cdn.nylocas.com/resources/icons/items/bones.png", TileIcons.imageUrl(tile));
		assertFalse(TileIcons.allowed("http://cdn.nylocas.com/resources/icons/items/bones.png"));
		assertTrue(TileIcons.allowed("https://cdn.nylocas.com/resources/icons/items/bones.png"));
		assertTrue(TileIcons.allowed("https://media.nylocas.com/tiles/abc.webp"));
		assertTrue(TileIcons.allowed("https://media.nylocas.com/tiles/abc.gif"));
	}

	@Test
	public void formatsTheSameCountsAsTheSite()
	{
		assertEquals("99999", TileIcons.formatCount(99_999));
		assertEquals("100K", TileIcons.formatCount(100_000));
		assertEquals("9999K", TileIcons.formatCount(9_999_999));
		assertEquals("10M", TileIcons.formatCount(10_000_000));
		assertEquals(new Color(0xffff00), TileIcons.countColor(99_999));
		assertEquals(new Color(0x00ff80), TileIcons.countColor(10_000_000));
	}

	@Test
	public void tilesAreFourWideByThreeTall()
	{
		assertEquals(27, EventPanel.cellHeight(36));
		assertEquals(3, EventPanel.cellHeight(4));
		assertEquals(1, EventPanel.cellHeight(1));
	}

	private static EventTile tile()
	{
		EventTile tile = new EventTile();
		tile.id = 4;
		return tile;
	}

	private static EventItem item(String key, int id)
	{
		EventItem item = new EventItem();
		item.key = key;
		item.runeliteIds = List.of(id);
		return item;
	}
}
