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

	static String approved(String teamName, String tileTitle, boolean complete, Color teamColor, Color tileColor)
	{
		String tile = accent(tileTitle, tileColor);
		String line = accent(teamName, teamColor) + "'s submission for " + tile + " got approved";
		if (complete)
		{
			line += " and that completed the tile: " + tile;
		}
		return line;
	}

	static String rejected(String teamName, String tileTitle, String reason, Color teamColor, Color tileColor, Color reasonColor)
	{
		String line = accent(teamName, teamColor) + "'s submission for " + accent(tileTitle, tileColor) + " got rejected";
		String why = accent(reason, reasonColor);
		if (!why.isEmpty())
		{
			line += ": " + why;
		}
		return line;
	}

	static String deleted(String teamName, String tileTitle, Color teamColor, Color tileColor)
	{
		return accent(teamName, teamColor) + "'s submission for " + accent(tileTitle, tileColor) + " got deleted";
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
