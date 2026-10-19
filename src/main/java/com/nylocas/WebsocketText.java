package com.nylocas;

import java.awt.Color;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

final class WebsocketText
{
	static final String OWN_DROP = "You got: {drop} for {tile}";
	static final String OWN_DROP_SUBMITTED = "You got: {drop} for {tile}, auto submitted";
	static final String TEAM_DROP = "{team} got a drop: {drop} for {tile}";
	static final String APPROVED = "{team}'s submission for {tile} got approved";
	static final String APPROVED_COMPLETE = "{team}'s submission for {tile} got approved and that completes the tile: {tile}";
	static final String REJECTED = "{team}'s submission for {tile} got rejected";
	static final String REJECTED_REASON = "{team}'s submission for {tile} got rejected: {reason}";
	static final String DELETED = "{team}'s submission for {tile} got deleted";
	static final String RANK_LEAD = "{team} took the lead!";
	static final String RANK_WIN = "{team} won the event! Congratulations!";
	static final String RANK_TIE = "First place is tied.";

	private WebsocketText()
	{
	}

	static String eventDrop(String template, String dropName, String tileName, String teamName, Color teamColor, Color dropColor, Color tileColor)
	{
		return format(template, teamName, dropName, tileName, "", teamColor, dropColor, tileColor, null);
	}

	static String teamEventDrop(String template, String teamName, String dropName, String tileName, Color teamColor, Color dropColor, Color tileColor)
	{
		return format(template, teamName, dropName, tileName, "", teamColor, dropColor, tileColor, null);
	}

	static String approved(String template, String teamName, String tileTitle, Color teamColor, Color tileColor)
	{
		return format(template, teamName, "", tileTitle, "", teamColor, null, tileColor, null);
	}

	static String rejected(String template, String teamName, String tileTitle, String reason, Color teamColor, Color tileColor, Color reasonColor)
	{
		return format(template, teamName, "", tileTitle, reason, teamColor, null, tileColor, reasonColor);
	}

	static String deleted(String template, String teamName, String tileTitle, Color teamColor, Color tileColor)
	{
		return format(template, teamName, "", tileTitle, "", teamColor, null, tileColor, null);
	}

	static String rank(String template, String leader, Color accent)
	{
		return format(template, leader, "", "", "", accent, null, null, null);
	}

	private static String format(String template, String team, String drop, String tile, String reason, Color teamColor, Color dropColor, Color tileColor, Color reasonColor)
	{
		String pattern = template == null ? "" : Text.removeTags(template);
		if (pattern.trim().isEmpty())
		{
			return "";
		}
		StringBuilder out = new StringBuilder();
		int cursor = 0;
		while (cursor < pattern.length())
		{
			int open = pattern.indexOf('{', cursor);
			if (open < 0)
			{
				out.append(pattern.substring(cursor));
				break;
			}
			int close = pattern.indexOf('}', open + 1);
			if (close < 0)
			{
				out.append(pattern.substring(cursor));
				break;
			}
			out.append(pattern, cursor, open);
			switch (pattern.substring(open + 1, close))
			{
				case "team":
					out.append(accent(team, teamColor));
					break;
				case "drop":
					out.append(accent(drop, dropColor));
					break;
				case "tile":
					out.append(accent(tile, tileColor));
					break;
				case "reason":
					out.append(accent(reason, reasonColor));
					break;
				default:
					out.append(pattern, open, close + 1);
					break;
			}
			cursor = close + 1;
		}
		return out.toString();
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
