package com.nylocas;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

final class TileText
{
	private TileText()
	{
	}

	static String tileTitle(EventTile tile)
	{
		if (tile.title == null || tile.title.isEmpty())
		{
			return "Tile " + tile.id;
		}
		return tile.title;
	}

	static String itemName(EventItem item, IntFunction<String> lookup)
	{
		if (item.runeliteIds != null && lookup != null)
		{
			for (Integer id : item.runeliteIds)
			{
				if (id == null)
				{
					continue;
				}
				String name = lookup.apply(id);
				if (name != null && !name.isEmpty() && !"null".equals(name))
				{
					return name;
				}
			}
		}
		return catalogName(item.key);
	}

	static String sourcesText(EventItem item)
	{
		if (item == null || item.sources == null || item.sources.isEmpty())
		{
			return "";
		}
		List<String> names = new ArrayList<>();
		for (EventSource source : item.sources)
		{
			if (source == null)
			{
				continue;
			}
			if (source.displayName != null && !source.displayName.isEmpty())
			{
				names.add(source.displayName);
			}
		}
		if (names.isEmpty())
		{
			return "Unknown source";
		}
		return String.join(", ", names);
	}

	static String requirement(EventTile tile)
	{
		int items = itemCount(tile);
		if (tile != null && tile.pool)
		{
			if (tile.need == 1)
			{
				return "1 drop";
			}
			return tile.need + " drops";
		}
		if (items <= 1)
		{
			return items == 1 ? "All" : "";
		}
		if (tile.need >= items)
		{
			return "All";
		}
		if (items == 2)
		{
			return "Any";
		}
		return "Any " + tile.need;
	}

	static String progress(EventTile tile)
	{
		int items = itemCount(tile);
		if (tile == null || items == 0)
		{
			return "";
		}
		if (tile.pool)
		{
			int got = 0;
			for (EventItem item : tile.items)
			{
				if (item != null)
				{
					got += item.approved;
				}
			}
			return got + "/" + tile.need;
		}
		if (items == 1)
		{
			EventItem item = firstItem(tile);
			return item.approved + "/" + item.count;
		}
		int done = 0;
		for (EventItem item : tile.items)
		{
			if (itemDone(tile, item))
			{
				done++;
			}
		}
		return done + "/" + tile.need;
	}

	static String itemFraction(EventTile tile, EventItem item)
	{
		if (item == null)
		{
			return "";
		}
		if (tile != null && tile.pool)
		{
			return Integer.toString(item.approved);
		}
		return item.approved + "/" + item.count;
	}

	static boolean itemDone(EventTile tile, EventItem item)
	{
		return tile != null && !tile.pool && item != null && item.count > 0 && item.approved >= item.count;
	}

	static String pendingText(EventItem item)
	{
		if (item == null || item.pending <= 0)
		{
			return "";
		}
		if (item.pending == 1)
		{
			return "1 pending";
		}
		return item.pending + " pending";
	}

	static String catalogName(String key)
	{
		if (key == null || key.isEmpty())
		{
			return "Unknown item";
		}
		String spaced = key.replace('_', ' ');
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private static int itemCount(EventTile tile)
	{
		if (tile == null || tile.items == null)
		{
			return 0;
		}
		int count = 0;
		for (EventItem item : tile.items)
		{
			if (item != null)
			{
				count++;
			}
		}
		return count;
	}

	private static EventItem firstItem(EventTile tile)
	{
		for (EventItem item : tile.items)
		{
			if (item != null)
			{
				return item;
			}
		}
		return null;
	}
}
