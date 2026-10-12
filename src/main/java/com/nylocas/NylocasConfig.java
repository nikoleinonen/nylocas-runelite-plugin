package com.nylocas;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("nylocas-clan-events")
public interface NylocasConfig extends Config
{
	@ConfigItem(
		keyName = "connect",
		name = "Connect to nylocas.com",
		description = "Load your team's event from nylocas.com",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean connect()
	{
		return false;
	}
}
