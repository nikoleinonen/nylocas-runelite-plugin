package com.nylocas;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.runelite.client.util.Text;

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

	static ProofTarget firstProof(List<EventTile> tiles, String sourceName, int itemId)
	{
		if (tiles == null || sourceName == null)
		{
			return null;
		}
		List<EventTile> ordered = new ArrayList<>();
		for (EventTile tile : tiles)
		{
			if (tile != null)
			{
				ordered.add(tile);
			}
		}
		ordered.sort(Comparator.comparingInt((EventTile tile) -> tile.row).thenComparingInt(tile -> tile.col));
		for (EventTile tile : ordered)
		{
			ProofTarget target = matchTile(tile, sourceName, itemId);
			if (target != null)
			{
				return target;
			}
		}
		return null;
	}

	static long playerId(List<EventPlayer> players, String name)
	{
		String key = playerKey(name);
		if (key.isEmpty() || players == null)
		{
			return 0;
		}
		for (EventPlayer player : players)
		{
			if (player != null && key.equals(playerKey(player.name)))
			{
				return player.id;
			}
		}
		return 0;
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

	private static ProofTarget matchTile(EventTile tile, String sourceName, int itemId)
	{
		if (tile.complete || tile.items == null)
		{
			return null;
		}
		for (EventItem item : tile.items)
		{
			if (item == null || item.key == null || item.key.isEmpty() || !hasRuneLiteId(item, itemId))
			{
				continue;
			}
			String sourceId = sourceId(item, sourceName);
			if (sourceId == null || !stillNeeds(tile, item))
			{
				continue;
			}
			return new ProofTarget(tile, item, sourceId);
		}
		return null;
	}

	private static boolean stillNeeds(EventTile tile, EventItem item)
	{
		if (tile.pool)
		{
			return openCount(tile) < tile.need;
		}
		return item.approved + item.pending < item.count;
	}

	private static int openCount(EventTile tile)
	{
		int count = 0;
		if (tile.items == null)
		{
			return 0;
		}
		for (EventItem item : tile.items)
		{
			if (item != null)
			{
				count += item.approved + item.pending;
			}
		}
		return count;
	}

	private static String sourceId(EventItem item, String sourceName)
	{
		if (item.sources == null)
		{
			return null;
		}
		for (EventSource source : item.sources)
		{
			if (source == null || source.id == null || source.id.isEmpty() || source.runeliteNames == null)
			{
				continue;
			}
			for (String name : source.runeliteNames)
			{
				if (sourceName.equals(name))
				{
					return source.id;
				}
			}
		}
		return null;
	}

	private static String playerKey(String name)
	{
		if (name == null)
		{
			return "";
		}
		return Text.removeTags(name).replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
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

	static final class ProofTarget
	{
		final EventTile tile;
		final EventItem item;
		final String sourceId;
		String itemName;

		ProofTarget(EventTile tile, EventItem item, String sourceId)
		{
			this.tile = tile;
			this.item = item;
			this.sourceId = sourceId;
		}
	}
}
