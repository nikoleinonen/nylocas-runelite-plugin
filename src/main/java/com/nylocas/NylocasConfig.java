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
	String RECEIVE_ENEMY = "receiveEnemy";
	String RECEIVE_ENEMY_REJECTIONS = "receiveEnemyRejections";
	String ENEMY_EVENT_DROP = "enemyEventDrop";

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
		description = "Event code and UTC time on the game screen. A code from the event replaces the one set here, if the organizer set one.",
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
		description = "Keep your team's board up to date while the session is connected. This setting also allows you to get notifications of various things listed under 'Notifications' section. To get automatic updates on event code, this must be enabled.",
		position = 1,
		section = eventSection
	)
	default boolean connectWebsocket()
	{
		return false;
	}

	@ConfigItem(
		keyName = "autoConnect",
		name = "Auto-connect/fetch event upon login",
		description = "Upon loggin in, connect or fetch the event automatically. 'Enable real-time updates' determines if you connect or only fetch it once.",
		position = 2,
		section = eventSection
	)
	default boolean autoConnect()
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
		keyName = "submitProofs",
		name = "Auto submit",
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
		keyName = "hidePrivateMessages",
		name = "Hide private messages",
		description = "Leave private messages out of the drop screenshot, both the local copy and the image sent to nylocas.com. The private chat can flicker for one frame. Split friends private chat must be enabled in the game settings, or those messages stay in the chat box.",
		position = 2,
		section = submissionsSection
	)
	default boolean hidePrivateMessages()
	{
		return false;
	}

	@ConfigItem(
		keyName = "openChat",
		name = "Open chat automatically",
		description = "Open the Game chat before a drop screenshot, then put the chat back. The drop line 'hopefully' stays in the picture for both the local copy and the image sent to nylocas.com.",
		position = 3,
		section = submissionsSection
	)
	default boolean openChat()
	{
		return false;
	}

	@ConfigItem(
		keyName = "receiveRank",
		name = "First place changes",
		description = "Show a chat message when a team takes the lead or wins the event.",
		position = 0,
		section = notificationsSection
	)
	default boolean receiveRank()
	{
		return false;
	}

	@ConfigItem(
		keyName = "eventDrop",
		name = "Own team drops",
		description = "Show a chat message when you receive a drop for an event tile, and when a teammate receives one.",
		position = 1,
		section = notificationsSection
	)
	default boolean eventDrop()
	{
		return false;
	}

	@ConfigItem(
		keyName = "receiveOwn",
		name = "Own team approvals",
		description = "Show a chat message when your team's submission is approved.",
		position = 2,
		section = notificationsSection
	)
	default boolean receiveOwn()
	{
		return false;
	}

	@ConfigItem(
		keyName = "receiveOwnRejections",
		name = "Own team rejections",
		description = "Show a chat message when your team's submission is rejected or deleted.",
		position = 3,
		section = notificationsSection
	)
	default boolean receiveOwnRejections()
	{
		return false;
	}

	@ConfigItem(
		keyName = ENEMY_EVENT_DROP,
		name = "Enemy team drops",
		description = "Show a chat message when an enemy team receives a drop for an event tile.",
		position = 4,
		section = notificationsSection
	)
	default boolean enemyEventDrop()
	{
		return false;
	}

	@ConfigItem(
		keyName = RECEIVE_ENEMY,
		name = "Enemy team approvals",
		description = "Show a chat message when an enemy team's submission is approved.",
		position = 5,
		section = notificationsSection
	)
	default boolean receiveEnemy()
	{
		return false;
	}

	@ConfigItem(
		keyName = RECEIVE_ENEMY_REJECTIONS,
		name = "Enemy team rejections",
		description = "Show a chat message when an enemy team's submission is rejected or deleted.",
		position = 6,
		section = notificationsSection
	)
	default boolean receiveEnemyRejections()
	{
		return false;
	}

	@ConfigItem(
		keyName = "dropColor",
		name = "Drop color",
		description = "Accent for drop names in chat notifications. The rest of the line stays the normal chat color.",
		position = 7,
		section = notificationsSection
	)
	default Color dropColor()
	{
		return new Color(0x1D78A3);
	}

	@ConfigItem(
		keyName = "tileColor",
		name = "Tile color",
		description = "Accent for tile names in chat notifications. The rest of the line stays the normal chat color.",
		position = 8,
		section = notificationsSection
	)
	default Color tileColor()
	{
		return new Color(0x1D78A3);
	}

	@ConfigItem(
		keyName = "rejectionColor",
		name = "Rejection color",
		description = "Accent for rejection reasons in chat notifications. The rest of the line stays the normal chat color.",
		position = 9,
		section = notificationsSection
	)
	default Color rejectionColor()
	{
		return new Color(0xB60B0B);
	}

	@ConfigItem(
		keyName = "ownTeamColor",
		name = "Own team color",
		description = "Accent for your team name, and for the leader when your team is in front. The rest of the line stays the normal chat color.",
		position = 10,
		section = notificationsSection
	)
	default Color ownTeamColor()
	{
		return new Color(0x208320);
	}

	@ConfigItem(
		keyName = "enemyTeamColor",
		name = "Enemy team color",
		description = "Accent for an opponent's team name, and for the leader when another team is in front. The rest of the line stays the normal chat color.",
		position = 11,
		section = notificationsSection
	)
	default Color enemyTeamColor()
	{
		return new Color(0xB60B0B);
	}

	@ConfigItem(
		keyName = "displayOverlay",
		name = "Display overlay",
		description = "Show the event code and the current UTC time on the game screen",
		position = 0,
		section = overlaySection
	)
	default boolean displayOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "eventCode",
		name = "Event code",
		description = "Used when the loaded event has no code. A code from the event replaces this. For this to update in real-time if organizer changes is, 'Enable real-time updates' must be enabled.",
		position = 1,
		section = overlaySection
	)
	default String eventCode()
	{
		return "";
	}

	@ConfigItem(
		keyName = "codeColor",
		name = "Code color",
		description = "Color of the event code",
		position = 2,
		section = overlaySection
	)
	default Color codeColor()
	{
		return Color.GREEN;
	}

	@ConfigItem(
		keyName = "timeColor",
		name = "Time color",
		description = "Color of the UTC time",
		position = 3,
		section = overlaySection
	)
	default Color timeColor()
	{
		return Color.WHITE;
	}
}
