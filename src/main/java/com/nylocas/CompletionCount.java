package com.nylocas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CompletionCount
{
	private static final long MAX_AGE_MS = 3 * 60 * 1000L;

	private static final Pattern KILL = Pattern.compile("Your (.+) kill count is: ([\\d,]+)\\b");
	private static final Pattern COMPLETED = Pattern.compile("Your completed (.+) count is: ([\\d,]+)\\b");

	final String name;
	final int count;

	private CompletionCount(String name, int count)
	{
		this.name = name;
		this.count = count;
	}

	static boolean isRecent(long seenAt)
	{
		return System.currentTimeMillis() - seenAt <= MAX_AGE_MS;
	}

	static CompletionCount parse(String message)
	{
		CompletionCount kill = match(KILL, message);
		if (kill != null)
		{
			return kill;
		}
		return parseCompleted(message);
	}

	static CompletionCount parseCompleted(String message)
	{
		return match(COMPLETED, message);
	}

	private static CompletionCount match(Pattern pattern, String message)
	{
		if (message == null)
		{
			return null;
		}
		Matcher matcher = pattern.matcher(message);
		if (!matcher.find())
		{
			return null;
		}
		String name = matcher.group(1).trim();
		if (name.isEmpty())
		{
			return null;
		}
		int count;
		try
		{
			count = Integer.parseInt(matcher.group(2).replace(",", ""));
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
		if (count < 1)
		{
			return null;
		}
		return new CompletionCount(name, count);
	}
}
