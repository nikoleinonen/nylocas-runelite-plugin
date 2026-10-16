package com.nylocas;

import java.awt.Color;
import java.awt.Image;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

final class TileIcons
{
	static final class Picture
	{
		final Image image;
		final boolean cover;
		final boolean sharp;
		final Integer count;

		Picture(Image image, boolean cover, boolean sharp, Integer count)
		{
			this.image = image;
			this.cover = cover;
			this.sharp = sharp;
			this.count = count;
		}
	}

	private static final Pattern QUOTES = Pattern.compile("['\u2019\u2018`\u00B4]");
	private static final Pattern ITEM_KEY = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
	private static final Pattern ITEM_ICON = Pattern.compile("https://cdn\\.nylocas\\.com/resources/icons/items/[a-z0-9][a-z0-9_-]{0,63}\\.png");
	private static final Pattern TILE_IMAGE = Pattern.compile("https://media\\.nylocas\\.com/tiles/[A-Za-z0-9_-]+\\.(?:png|gif|jpg|webp)");

	private TileIcons()
	{
	}

	static String imageUrl(EventTile tile)
	{
		if (covers(tile) || customIcon(tile))
		{
			return tile.imageUrl;
		}
		return catalogIconUrl(tile);
	}

	static String catalogIconUrl(EventTile tile)
	{
		return itemIconUrl(iconKey(tile));
	}

	static boolean covers(EventTile tile)
	{
		return tile != null && "tile".equals(tile.imageFit) && allowedMedia(tile.imageUrl);
	}

	static boolean customIcon(EventTile tile)
	{
		return tile != null && "icon".equals(tile.imageFit) && allowedMedia(tile.imageUrl);
	}

	static Integer count(EventTile tile)
	{
		if (tile == null || covers(tile) || tile.iconCount == null || tile.iconCount < 1)
		{
			return null;
		}
		return tile.iconCount;
	}

	static boolean allowed(String url)
	{
		return url != null && (ITEM_ICON.matcher(url).matches() || TILE_IMAGE.matcher(url).matches());
	}

	static String formatCount(int value)
	{
		if (value >= 10_000_000)
		{
			return (value / 1_000_000) + "M";
		}
		if (value >= 100_000)
		{
			return (value / 1_000) + "K";
		}
		return Integer.toString(value);
	}

	static Color countColor(int value)
	{
		if (value >= 10_000_000)
		{
			return new Color(0x00ff80);
		}
		if (value >= 100_000)
		{
			return Color.WHITE;
		}
		return new Color(0xffff00);
	}

	static Integer runeLiteId(EventTile tile)
	{
		if (tile == null || tile.items == null)
		{
			return null;
		}
		String key = normalizeKey(iconKey(tile));
		EventItem fallback = null;
		for (EventItem item : tile.items)
		{
			if (item == null)
			{
				continue;
			}
			if (fallback == null)
			{
				fallback = item;
			}
			if (!key.isEmpty() && key.equals(normalizeKey(item.key)))
			{
				return firstId(item.runeliteIds);
			}
		}
		return fallback == null ? null : firstId(fallback.runeliteIds);
	}

	static Picture picture(EventTile tile, Image image, boolean catalog)
	{
		if (tile == null || image == null)
		{
			return null;
		}
		boolean cover = !catalog && covers(tile);
		boolean sharp = !cover && (catalog || !customIcon(tile));
		return new Picture(image, cover, sharp, cover ? null : count(tile));
	}

	private static String itemIconUrl(String key)
	{
		String normalized = normalizeKey(key);
		if (!ITEM_KEY.matcher(normalized).matches())
		{
			return null;
		}
		return "https://cdn.nylocas.com/resources/icons/items/" + normalized + ".png";
	}

	private static String iconKey(EventTile tile)
	{
		if (tile == null)
		{
			return null;
		}
		if (tile.iconItem != null && !tile.iconItem.isBlank())
		{
			return tile.iconItem;
		}
		if (tile.items == null)
		{
			return null;
		}
		for (EventItem item : tile.items)
		{
			if (item != null && item.key != null && !item.key.isBlank())
			{
				return item.key;
			}
		}
		return null;
	}

	private static boolean allowedMedia(String url)
	{
		return url != null && TILE_IMAGE.matcher(url).matches();
	}

	static String normalizeKey(String key)
	{
		if (key == null)
		{
			return "";
		}
		String normalized = QUOTES.matcher(key.trim().toLowerCase(Locale.ROOT)).replaceAll("");
		normalized = normalized.replaceAll("[()]", "");
		normalized = normalized.replaceAll("\\s+", "_");
		normalized = normalized.replaceAll("_+", "_");
		return normalized.replaceAll("^[_-]+|[_-]+$", "");
	}

	private static Integer firstId(List<Integer> ids)
	{
		if (ids == null)
		{
			return null;
		}
		for (Integer id : ids)
		{
			if (id != null)
			{
				return id;
			}
		}
		return null;
	}
}
