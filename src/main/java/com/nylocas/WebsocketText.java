package com.nylocas;

import java.awt.Color;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

final class WebsocketText
{
	private WebsocketText()
	{
	}

	static String eventDrop(String dropName, String tileName, boolean autoSubmitted, Color dropColor, Color tileColor)
	{
		String line = "Event drop: " + accent(dropName, dropColor) + " for " + accent(tileName, tileColor);
		if (autoSubmitted)
		{
			line += ", auto submitted";
		}
		return line;
	}

	static String approved(String teamName, String item, boolean complete, String tileTitle, Color teamColor, Color dropColor, Color tileColor)
	{
		String line = accent(teamName, teamColor) + "'s drop " + accent(item, dropColor) + " got approved";
		if (complete)
		{
			line += " and that completed the tile: " + accent(tileTitle, tileColor);
		}
		return line;
	}

	static String rank(String leader, boolean winner, Color accent)
	{
		if (leader == null || leader.isEmpty())
		{
			return "First place is tied.";
		}
		String name = accent(leader, accent);
		if (winner)
		{
			return name + " won the event! Congratulations!";
		}
		return name + " took the lead!";
	}

	private static String accent(String value, Color color)
	{
		String text = value == null ? "" : Text.removeTags(value);
		if (text.isEmpty() || color == null)
		{
			return text;
		}
		return ColorUtil.wrapWithColorTag(text, color);
	}
}
