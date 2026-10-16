package com.nylocas;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CompletionCount
{
	private static final Pattern KILL = Pattern.compile("Your (.+) kill count is: ([\\d,]+)\\b");
	private static final Pattern COMPLETED = Pattern.compile("Your (?:completed|subdued) (.+) count is: ([\\d,]+)\\b");

	final String name;
	final int count;

	private CompletionCount(String name, int count)
	{
		this.name = name;
		this.count = count;
	}

	static CompletionCount parse(String message)
	{
		if (message == null)
		{
			return null;
		}
		Matcher matcher = KILL.matcher(message);
		if (!matcher.find())
		{
			matcher = COMPLETED.matcher(message);
			if (!matcher.find())
			{
				return null;
			}
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
