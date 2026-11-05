package com.nylocas;

import java.util.List;
import java.util.Locale;
import net.runelite.client.util.Text;

final class EventPlayer
{
	long id;
	String name;

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

	private static String playerKey(String name)
	{
		if (name == null)
		{
			return "";
		}
		return Text.removeTags(name).replace('\u00A0', ' ').trim().toLowerCase(Locale.ROOT);
	}
}
