package com.nylocas;

import java.util.regex.Pattern;

final class DiscordWebhook
{
	private static final int MAX_LENGTH = 200;
	private static final Pattern WEBHOOK = Pattern.compile("^https://discord\\.com/api/webhooks/[0-9]+/[A-Za-z0-9_-]+$");

	private DiscordWebhook()
	{
	}

	static String allowed(String value)
	{
		if (value == null)
		{
			return "";
		}
		String webhook = value.trim();
		if (webhook.length() > MAX_LENGTH || !WEBHOOK.matcher(webhook).matches())
		{
			return "";
		}
		return webhook;
	}
}
