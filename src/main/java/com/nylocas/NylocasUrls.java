package com.nylocas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class NylocasUrls
{
	static final String API = "https://api.nylocas.com";
	static final String WEBSOCKET = "wss://ws.nylocas.com";
	static final String WEBSITE = "https://nylocas.com";
	static final String CDN = "https://cdn.nylocas.com";
	static final String MEDIA = "https://media.nylocas.com";
	static final String DISCORD = "https://discord.gg/3T23DAehU5";
	static final String GITHUB = "https://github.com/nikoleinonen/nylocas-runelite-plugin";
	static final String ITEM_ICONS = CDN + "/resources/icons/items/";
	static final String TILE_IMAGES = MEDIA + "/tiles/";

	private NylocasUrls()
	{
	}

	static String hashKey(String key)
	{
		try
		{
			byte[] sum = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
			char[] hex = new char[sum.length * 2];
			for (int i = 0; i < sum.length; i++)
			{
				int value = sum[i] & 0xff;
				hex[i * 2] = "0123456789abcdef".charAt(value >>> 4);
				hex[i * 2 + 1] = "0123456789abcdef".charAt(value & 0x0f);
			}
			return new String(hex);
		}
		catch (NoSuchAlgorithmException ex)
		{
			throw new IllegalStateException(ex);
		}
	}

	static String pluginEvent(String key)
	{
		return API + "/plugin/event/" + hashKey(key);
	}

	static String pluginEvent(String key, int openRow)
	{
		return pluginEvent(key) + "?open_row=" + openRow;
	}

	static String pluginDrop()
	{
		return API + "/plugin/drop";
	}

	static String pluginSubmission()
	{
		return API + "/plugin/submission";
	}

	static String itemIcon(String key)
	{
		return ITEM_ICONS + key + ".png";
	}

	static String websocket(String keyHash, boolean rank, boolean own, boolean enemy, boolean enemyReject, boolean enemyDrop)
	{
		return WEBSOCKET + "/plugin/websocket/" + keyHash
			+ "?rank=" + (rank ? "1" : "0")
			+ "&own=" + (own ? "1" : "0")
			+ "&enemy=" + (enemy ? "1" : "0")
			+ "&enemy_reject=" + (enemyReject ? "1" : "0")
			+ "&enemy_drop=" + (enemyDrop ? "1" : "0");
	}

	static String event(String type, long eventId)
	{
		String slug = eventSlug(type);
		if (slug.isEmpty() || eventId <= 0)
		{
			return WEBSITE + "/";
		}
		return WEBSITE + "/events/" + slug + "/" + eventId + "/overview";
	}

	private static String eventSlug(String type)
	{
		if ("classic_bingo".equals(type))
		{
			return "classic-bingo";
		}
		if ("levels_bingo".equals(type))
		{
			return "levels-bingo";
		}
		return "";
	}
}
