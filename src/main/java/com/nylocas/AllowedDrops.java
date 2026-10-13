package com.nylocas;

import java.util.ArrayList;
import java.util.List;

final class AllowedDrops
{
	private AllowedDrops()
	{
	}

	static boolean allowsSource(List<EventTile> tiles, String sourceName)
	{
		if (tiles == null || sourceName == null)
		{
			return false;
		}
		for (EventTile tile : tiles)
		{
			if (tile == null || tile.items == null)
			{
				continue;
			}
			for (EventItem item : tile.items)
			{
				if (item != null && hasSource(item, sourceName))
				{
					return true;
				}
			}
		}
		return false;
	}

	static List<String> matchingTitles(List<EventTile> tiles, String sourceName, int itemId)
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
			if (item == null || !hasRuneLiteId(item, itemId) || !hasSource(item, sourceName))
			{
				continue;
			}
			return true;
		}
		return false;
	}

	private static boolean hasRuneLiteId(EventItem item, int itemId)
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
