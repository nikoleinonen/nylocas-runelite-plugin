package com.nylocas;

import java.awt.Color;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Text;

final class LoginText
{
	private static final String CONNECT_REMINDER = "Connect to the Nylocas event via the side panel. Clear the team key to not get this message again.";
	private static final String FETCH_REMINDER = "Fetch the Nylocas event via the side panel. Clear the team key to not get this message again.";

	private LoginText()
	{
	}

	static String connected(String eventName, String teamName)
	{
		return red("Connected to Nylocas event " + plain(eventName) + " as " + plain(teamName));
	}

	static String fetched(String eventName, String teamName)
	{
		return red("Fetched Nylocas event " + plain(eventName) + " as " + plain(teamName));
	}

	static String reconnected(String eventName, String teamName)
	{
		return red("Reconnected to Nylocas event " + plain(eventName) + " as " + plain(teamName));
	}

	static String reminder(boolean realtime)
	{
		return red(realtime ? CONNECT_REMINDER : FETCH_REMINDER);
	}

	private static String red(String line)
	{
		return ColorUtil.wrapWithColorTag(line, Color.RED);
	}

	private static String plain(String value)
	{
		return value == null ? "" : Text.removeTags(value);
	}
}
