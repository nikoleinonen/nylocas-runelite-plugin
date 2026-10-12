package com.nylocas;

import com.google.gson.annotations.SerializedName;
import java.util.List;

final class EventBoard
{
	int rows;
	int cols;

	@SerializedName("team_name")
	String teamName;

	String rank;

	@SerializedName("open_row")
	int openRow;

	List<EventTile> tiles;
}

final class EventTile
{
	long id;
	int row;
	int col;
	String title;
	boolean complete;
	boolean partial;
	int need;
	boolean pool;
	List<EventItem> items;
}

final class EventItem
{
	@SerializedName("item")
	String key;

	int count;
	int approved;
	int pending;

	@SerializedName("runelite_ids")
	List<Integer> runeliteIds;

	List<EventSource> sources;
}

final class EventSource
{
	@SerializedName("name")
	String displayName;

	@SerializedName("runelite_names")
	List<String> runeliteNames;
}
