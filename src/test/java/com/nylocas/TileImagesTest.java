package com.nylocas;

import java.awt.Color;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TileImagesTest
{
	@Test
	public void catalogIcon()
	{
		EventTile tile = tile();
		tile.iconItem = "Twisted Bow";
		tile.items = List.of(item("bandos_hilt", 11832));

		assertEquals("https://cdn.nylocas.com/resources/icons/items/twisted_bow.png", TileImages.imageUrl(tile));
		assertEquals("https://cdn.nylocas.com/resources/icons/items/twisted_bow.png", TileImages.catalogIconUrl(tile));
		assertFalse(TileImages.covers(tile));
		assertEquals(Integer.valueOf(11832), TileImages.runeLiteId(tile));
	}

	@Test
	public void firstItem()
	{
		EventTile tile = tile();
		tile.items = List.of(item("crystal_weapon_seed", 4207));

		assertEquals("https://cdn.nylocas.com/resources/icons/items/crystal_weapon_seed.png", TileImages.imageUrl(tile));
		assertEquals(Integer.valueOf(4207), TileImages.runeLiteId(tile));
	}

	@Test
	public void chosenIcon()
	{
		EventTile tile = tile();
		tile.iconItem = "tzrek-jad";
		tile.items = List.of(item("fire_cape", 6570));

		assertEquals("https://cdn.nylocas.com/resources/icons/items/tzrek-jad.png", TileImages.imageUrl(tile));
		assertEquals(Integer.valueOf(6570), TileImages.runeLiteId(tile));
	}

	@Test
	public void tileImage()
	{
		EventTile tile = tile();
		tile.imageUrl = "https://media.nylocas.com/tiles/abc_DEF-123.png";
		tile.imageFit = "tile";
		tile.iconCount = 5;
		tile.items = List.of(item("twisted_bow", 20997));

		assertTrue(TileImages.covers(tile));
		assertEquals(tile.imageUrl, TileImages.imageUrl(tile));
		assertNull(TileImages.count(tile));
		assertEquals("https://cdn.nylocas.com/resources/icons/items/twisted_bow.png", TileImages.catalogIconUrl(tile));
	}

	@Test
	public void customIcon()
	{
		EventTile tile = tile();
		tile.imageUrl = "https://media.nylocas.com/tiles/abc.gif";
		tile.imageFit = "icon";
		tile.iconCount = 100500;

		assertTrue(TileImages.customIcon(tile));
		assertFalse(TileImages.covers(tile));
		assertEquals(tile.imageUrl, TileImages.imageUrl(tile));
		assertEquals(Integer.valueOf(100500), TileImages.count(tile));
		assertEquals("100K", TileImages.formatCount(100500));
		assertEquals(Color.WHITE, TileImages.countColor(100500));
	}

	@Test
	public void imageUrl()
	{
		EventTile tile = tile();
		tile.imageUrl = "https://example.com/tiles/abc.png";
		tile.imageFit = "tile";
		tile.items = List.of(item("bones", 526));

		assertFalse(TileImages.allowed(tile.imageUrl));
		assertEquals("https://cdn.nylocas.com/resources/icons/items/bones.png", TileImages.imageUrl(tile));
		assertFalse(TileImages.allowed("http://cdn.nylocas.com/resources/icons/items/bones.png"));
		assertTrue(TileImages.allowed("https://cdn.nylocas.com/resources/icons/items/bones.png"));
		assertTrue(TileImages.allowed("https://media.nylocas.com/tiles/abc.webp"));
		assertTrue(TileImages.allowed("https://media.nylocas.com/tiles/abc.gif"));
	}

	@Test
	public void countFormat()
	{
		assertEquals("99999", TileImages.formatCount(99_999));
		assertEquals("100K", TileImages.formatCount(100_000));
		assertEquals("9999K", TileImages.formatCount(9_999_999));
		assertEquals("10M", TileImages.formatCount(10_000_000));
		assertEquals(new Color(0xffff00), TileImages.countColor(99_999));
		assertEquals(new Color(0x00ff80), TileImages.countColor(10_000_000));
	}

	@Test
	public void cellHeight()
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
