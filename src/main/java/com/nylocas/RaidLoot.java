package com.nylocas;

final class RaidLoot
{
	private static final long CHAT_NAME_MAX_AGE_MS = 5 * 60 * 1000L;

	private RaidLoot()
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
		CompletionCount count = CompletionCount.parseCompleted(message);
		if (count == null)
		{
			return null;
		}
		if (count.name.startsWith("Chambers of Xeric")
			|| count.name.startsWith("Theatre of Blood")
			|| count.name.startsWith("Tombs of Amascut"))
		{
			return count.name;
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
