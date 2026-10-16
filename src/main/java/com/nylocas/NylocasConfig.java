package com.nylocas;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(NylocasConfig.GROUP)
public interface NylocasConfig extends Config
{
	String GROUP = "nylocas-clan-events";
	String TEAM_KEY = "teamKey";

	@ConfigItem(
		keyName = "connect",
		name = "Connect to nylocas.com",
		description = "Allow the client to communicate with the nylocas.com backend to retrieve information and automatically submit proof.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default boolean connect()
	{
		return false;
	}

	@ConfigItem(
		keyName = TEAM_KEY,
		name = "Team key",
		description = "Your team's event key",
		secret = true
	)
	default String teamKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "submitProofs",
		name = "Automatically send proof image",
		description = "Send a screenshot when you get a drop for an event tile"
	)
	default boolean submitProofs()
	{
		return true;
	}

	@ConfigItem(
		keyName = "saveProofs",
		name = "Save proof image locally",
		description = "Store a screenshot when you get a drop for an event tile"
	)
	default boolean saveProofs()
	{
		return true;
	}
}
