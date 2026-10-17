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
	String CONNECT_WEBSOCKET = "connectWebsocket";

	@ConfigSection(
		name = "Event",
		description = "Backend connection and your team key",
		position = 0
	)
	String eventSection = "event";

	@ConfigSection(
		name = "Submissions",
		description = "Proof images sent to the backend and saved locally.",
		position = 1
	)
	String submissionsSection = "submissions";

	@ConfigSection(
		name = "Notifications",
		description = "Chat notifications",
		position = 2
	)
	String notificationsSection = "notifications";

	@ConfigSection(
		name = "Overlay",
		description = "Event password and UTC time on the game screen. Password comes from the backend, no need to set it manually.",
		position = 3
	)
	String overlaySection = "overlay";

	@ConfigItem(
		keyName = "connect",
		name = "Connect to nylocas.com",
		description = "Allow the client to communicate with the nylocas.com backend to retrieve information and submit proofs. This must be enabled for anything to work.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0,
		section = eventSection
	)
	default boolean connect()
	{
		return false;
	}

	@ConfigItem(
		keyName = CONNECT_WEBSOCKET,
		name = "Enable real-time updates",
		description = "Keep your team's board up to date while the session is connected. Takes effect the next time you load the event.",
		position = 1,
		section = eventSection
	)
	default boolean connectWebsocket()
	{
		return false;
	}

	@ConfigItem(
		keyName = TEAM_KEY,
		name = "Team key",
		description = "Your team's event key",
		secret = true,
		position = 2,
		section = eventSection
	)
	default String teamKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "submitProofs",
		name = "Auto submission",
		description = "Automatically send a screenshot when you get a drop for an event tile. It is still recommended you take a screenshot yourself just in case.",
		position = 0,
		section = submissionsSection
	)
	default boolean submitProofs()
	{
		return false;
	}

	@ConfigItem(
		keyName = "saveProofs",
		name = "Save locally",
		description = "Store a screenshot locally when you get a drop for an event tile.",
		position = 1,
		section = submissionsSection
	)
	default boolean saveProofs()
	{
		return false;
	}

	@ConfigItem(
		keyName = "receiveRank",
		name = "Position changes",
		description = "Show a chat message when a team takes the lead. The place on the panel still updates while Enable real-time updates is on.",
		position = 0,
		section = notificationsSection
	)
	default boolean receiveRank()
	{
		return false;
	}

	@ConfigItem(
		keyName = "receiveOwn",
		name = "Own team approvals",
		description = "Show a chat message when your team's submission is approved. The tile on the panel still updates while Enable real-time updates is on.",
		position = 1,
		section = notificationsSection
	)
	default boolean receiveOwn()
	{
		return false;
	}

	@ConfigItem(
		keyName = "receiveEnemy",
		name = "Enemy team approvals",
		description = "Show a chat message when an opponent team's submission is approved. Requires Enable real-time updates. Takes effect on the next Connect.",
		position = 2,
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
		position = 3,
		section = notificationsSection
	)
	default Color ownTeamColor()
	{
		return new Color(0x208320);
	}

	@ConfigItem(
		keyName = "enemyTeamColor",
		name = "Enemy team color",
		description = "Accent for an opponent's team name, item, and tile, and for the leader when another team is in front. The rest of the line stays the normal chat color.",
		position = 4,
		section = notificationsSection
	)
	default Color enemyTeamColor()
	{
		return new Color(0xB60B0B);
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
