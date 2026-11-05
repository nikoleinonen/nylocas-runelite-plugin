package com.nylocas;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DiscordWebhookTest
{
	@Test
	public void allowsDiscordWebhook()
	{
		assertEquals(
			"https://discord.com/api/webhooks/1561855291425816656/abc_DEF-123",
			DiscordWebhook.allowed("https://discord.com/api/webhooks/1561855291425816656/abc_DEF-123"));
		assertEquals(
			"https://discord.com/api/webhooks/1/a",
			DiscordWebhook.allowed("  https://discord.com/api/webhooks/1/a  "));
	}

	@Test
	public void rejectsAnythingElse()
	{
		assertEquals("", DiscordWebhook.allowed(null));
		assertEquals("", DiscordWebhook.allowed(""));
		assertEquals("", DiscordWebhook.allowed("   "));
		assertEquals("", DiscordWebhook.allowed("https://example.com/api/webhooks/1/a"));
		assertEquals("", DiscordWebhook.allowed("https://discord.com/api/webhooks/1/a?wait=true"));
		assertEquals("", DiscordWebhook.allowed("https://discordapp.com/api/webhooks/1/a"));
		assertEquals("", DiscordWebhook.allowed("http://discord.com/api/webhooks/1/a"));
		assertEquals("", DiscordWebhook.allowed("https://discord.com/api/webhooks/1/" + "a".repeat(200)));
	}
}
