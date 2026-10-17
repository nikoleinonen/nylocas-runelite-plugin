package com.nylocas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RaidSource
{
	private static final long CHAT_NAME_MAX_AGE_MS = 5 * 60 * 1000L;

	private static final Pattern COMPLETED = Pattern.compile("Your (?:completed|subdued) (.+) count is: [\\d,]+\\b");

	private RaidSource()
	{
	}

	static boolean chatNameIsRecent(long seenAt)
	{
		return System.currentTimeMillis() - seenAt <= CHAT_NAME_MAX_AGE_MS;
	}

	static boolean isChest(String lootName)
	{
		return "Chambers of Xeric".equals(lootName)
			|| "Theatre of Blood".equals(lootName)
			|| "Tombs of Amascut".equals(lootName);
	}

	static String fromKillCount(String message)
	{
		if (message == null)
		{
			return null;
		}
		Matcher matcher = COMPLETED.matcher(message);
		if (!matcher.find())
		{
			return null;
		}
		String name = matcher.group(1).trim();
		if (name.startsWith("Chambers of Xeric")
			|| name.startsWith("Theatre of Blood")
			|| name.startsWith("Tombs of Amascut"))
		{
			return name;
		}
		return null;
	}

	static String resolve(String lootName, String chatName)
	{
		if (lootName == null)
		{
			return null;
		}
		if (!isChest(lootName))
		{
			return lootName;
		}
		if (chatName != null && chatName.startsWith(lootName))
		{
			return chatName;
		}
		return null;
	}
}
