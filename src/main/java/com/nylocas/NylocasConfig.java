package com.nylocas;

import java.awt.Color;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(NylocasConfig.GROUP)
public interface NylocasConfig extends Config
{
	String GROUP = "nylocas-clan-events";
	String TEAM_KEY = "teamKey";

	@ConfigSection(
		name = "Event",
		description = "Backend connection and proof image settings",
		position = 0
	)
	String eventSection = "event";

	@ConfigSection(
		name = "Notifications",
		description = "Game chat when a drop is approved or first place changes. You must reconnect for the settings to update.",
		position = 1
	)
	String notificationsSection = "notifications";

	@ConfigSection(
		name = "Overlay",
		description = "Event password and UTC time on the game screen. Password comes from the backend, no need to set it manually.",
		position = 2
	)
	String overlaySection = "overlay";

	@ConfigItem(
		keyName = "connect",
		name = "Connect to nylocas.com",
		description = "Allow the client to communicate with the nylocas.com backend to retrieve information and submit proof.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0,
		section = eventSection
	)
	default boolean connect()
	{
		return false;
	}

	@ConfigItem(
		keyName = "submitProofs",
		name = "Automatically send proof image",
		description = "Automatically send a screenshot to nylocas.com backend when you get a drop for an event tile",
		position = 1,
		section = eventSection
	)
	default boolean submitProofs()
	{
		return false;
	}

	@ConfigItem(
		keyName = "saveProofs",
		name = "Save proof image locally",
		description = "Store a screenshot when you get a drop for an event tile (same image that would go to the backend)",
		position = 2,
		section = eventSection
	)
	default boolean saveProofs()
	{
		return false;
	}

	@ConfigItem(
		keyName = TEAM_KEY,
		name = "Team key",
		description = "Your team's event key",
		secret = true,
		position = 3,
		section = eventSection
	)
	default String teamKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "receiveNotifications",
		name = "Receive notifications",
		description = "Receive real-time chat notifications about important events.",
		position = 0,
		section = notificationsSection
	)
	default boolean receiveNotifications()
	{
		return false;
	}

	@ConfigItem(
		keyName = "receiveRank",
		name = "Position changes",
		description = "Get a chat notification whenever a team takes the lead.",
		position = 1,
		section = notificationsSection
	)
	default boolean receiveRank()
	{
		return false;
	}

	@ConfigItem(
		keyName = "receiveOwn",
		name = "Own team submissions",
		description = "Get a chat notification when your team's submission is approved.",
		position = 2,
		section = notificationsSection
	)
	default boolean receiveOwn()
	{
		return false;
	}

	@ConfigItem(
		keyName = "receiveEnemy",
		name = "Enemy team submissions",
		description = "Get a chat notification when an opponent team's submission is approved.",
		position = 3,
		section = notificationsSection
	)
	default boolean receiveEnemy()
	{
		return false;
	}

	@ConfigItem(
		keyName = "ownTeamColor",
		name = "Own team color",
		description = "Accent for your team name, its item, and its tile, and for the leader when your team is in front. The rest of the line stays the normal chat color.",
		position = 4,
		section = notificationsSection
	)
	default Color ownTeamColor()
	{
		return new Color(0x3DDC97);
	}

	@ConfigItem(
		keyName = "enemyTeamColor",
		name = "Enemy team color",
		description = "Accent for an opponent's team name, item, and tile, and for the leader when another team is in front. The rest of the line stays the normal chat color.",
		position = 5,
		section = notificationsSection
	)
	default Color enemyTeamColor()
	{
		return new Color(0xFF6B6B);
	}

	@ConfigItem(
		keyName = "displayOverlay",
		name = "Display overlay",
		description = "Show the event password and the current UTC time on the game screen",
		position = 0,
		section = overlaySection
	)
	default boolean displayOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "passwordColor",
		name = "Password color",
		description = "Color of the event password",
		position = 1,
		section = overlaySection
	)
	default Color passwordColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
		keyName = "timeColor",
		name = "Time color",
		description = "Color of the UTC time",
		position = 2,
		section = overlaySection
	)
	default Color timeColor()
	{
		return Color.WHITE;
	}
}
