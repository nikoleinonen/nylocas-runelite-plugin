package com.nylocas;

import com.google.gson.annotations.SerializedName;
import java.util.List;

final class EventBoard
{
	int rows;
	int cols;

	@SerializedName("team_name")
	String teamName;

	@SerializedName("event_name")
	String eventName;

	@SerializedName("event_code")
	String eventCode;

	@SerializedName("discord_webhook_url")
	String discordWebhookUrl;

	@SerializedName("event_started")
	boolean eventStarted;

	@SerializedName("starts_at")
	String startsAt;

	@SerializedName("ends_at")
	String endsAt;

	String rank;

	@SerializedName("open_row")
	int openRow;

	List<EventPlayer> players;

	List<EventTile> tiles;
}
