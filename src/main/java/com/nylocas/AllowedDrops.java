package com.nylocas;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

final class EventBoard
{
	int rows;
	int cols;

	@SerializedName("team_name")
	String teamName;

	@SerializedName("open_row")
	int openRow;

	List<EventTile> tiles;
}

final class EventTile
{
	long id;
	int row;
	int col;
	String title;
	boolean complete;
	boolean partial;
	int need;
	boolean pool;
	List<EventItem> items;
}

final class EventItem
{
	String item;
	int count;
	int approved;
	int pending;

	@SerializedName("runelite_ids")
	List<Integer> runeliteIds;

	List<EventSource> sources;
}

final class EventSource
{
	String name;

	@SerializedName("runelite_names")
	List<String> runeliteNames;
}

final class ListedDrop
{
	final String tile;
	final String item;
	final String sources;

	ListedDrop(String tile, String item, String sources)
	{
		this.tile = tile;
		this.item = item;
		this.sources = sources;
	}
}

final class AllowedDrops
{
	private AllowedDrops()
	{
	}

	static List<ListedDrop> describe(List<EventTile> tiles, IntFunction<String> itemName)
	{
		List<ListedDrop> lines = new ArrayList<>();
		if (tiles == null)
		{
			return lines;
		}
		for (EventTile tile : tiles)
		{
			if (tile == null || tile.items == null)
			{
				continue;
			}
			String tileTitle = tile.title == null || tile.title.isEmpty() ? "Tile " + tile.id : tile.title;
			for (EventItem item : tile.items)
			{
				if (item == null)
				{
					continue;
				}
				lines.add(new ListedDrop(tileTitle, itemLabel(item, itemName), sourceLabel(item)));
			}
		}
		return lines;
	}

	private static String itemLabel(EventItem item, IntFunction<String> itemName)
	{
		if (item.runeliteIds != null && itemName != null)
		{
			for (Integer id : item.runeliteIds)
			{
				if (id == null)
				{
					continue;
				}
				String name = itemName.apply(id);
				if (name != null && !name.isEmpty() && !"null".equals(name))
				{
					return name;
				}
			}
		}
		return fallbackName(item.item);
	}

	private static String fallbackName(String key)
	{
		if (key == null || key.isEmpty())
		{
			return "Unknown item";
		}
		String spaced = key.replace('_', ' ');
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private static String sourceLabel(EventItem item)
	{
		if (item.sources == null || item.sources.isEmpty())
		{
			return "Unknown source";
		}
		List<String> names = new ArrayList<>();
		for (EventSource source : item.sources)
		{
			if (source == null)
			{
				continue;
			}
			if (source.name != null && !source.name.isEmpty())
			{
				names.add(source.name);
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
		int rows = itemRows(tile);
		if (tile != null && tile.pool)
		{
			if (tile.need == 1)
			{
				return "1 drop from this list";
			}
			return tile.need + " drops from this list";
		}
		if (rows <= 1)
		{
			return rows == 1 ? "All of these" : "";
		}
		if (tile.need >= rows)
		{
			return "All of these";
		}
		if (rows == 2)
		{
			return "Any one of these";
		}
		return "Any " + tile.need + " of these";
	}

	static String progress(EventTile tile)
	{
		int rows = itemRows(tile);
		if (tile == null || rows == 0)
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
		if (rows == 1)
		{
			EventItem item = firstItem(tile);
			return item.approved + "/" + item.count;
		}
		int done = 0;
		for (EventItem item : tile.items)
		{
			if (rowDone(tile, item))
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

	static boolean rowDone(EventTile tile, EventItem item)
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

	static String sourcesText(EventItem item)
	{
		if (item == null || item.sources == null || item.sources.isEmpty())
		{
			return "";
		}
		return sourceLabel(item);
	}

	private static int itemRows(EventTile tile)
	{
		if (tile == null || tile.items == null)
		{
			return 0;
		}
		int rows = 0;
		for (EventItem item : tile.items)
		{
			if (item != null)
			{
				rows++;
			}
		}
		return rows;
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

	static List<String> titles(List<EventTile> tiles, String sourceName, int itemId)
	{
		List<String> titles = new ArrayList<>();
		if (tiles == null || sourceName == null)
		{
			return titles;
		}
		for (EventTile tile : tiles)
		{
			if (tile == null || !matches(tile, sourceName, itemId))
			{
				continue;
			}
			if (tile.title == null || tile.title.isEmpty())
			{
				titles.add("tile " + tile.id);
				continue;
			}
			titles.add(tile.title);
		}
		return titles;
	}

	private static boolean matches(EventTile tile, String sourceName, int itemId)
	{
		if (tile.items == null)
		{
			return false;
		}
		for (EventItem item : tile.items)
		{
			if (item == null || !hasItem(item, itemId) || !hasSource(item, sourceName))
			{
				continue;
			}
			return true;
		}
		return false;
	}

	private static boolean hasItem(EventItem item, int itemId)
	{
		if (item.runeliteIds == null)
		{
			return false;
		}
		for (Integer id : item.runeliteIds)
		{
			if (id != null && id == itemId)
			{
				return true;
			}
		}
		return false;
	}

	private static boolean hasSource(EventItem item, String sourceName)
	{
		if (item.sources == null)
		{
			return false;
		}
		for (EventSource source : item.sources)
		{
			if (source == null || source.runeliteNames == null)
			{
				continue;
			}
			for (String name : source.runeliteNames)
			{
				if (sourceName.equals(name))
				{
					return true;
				}
			}
		}
		return false;
	}
}
