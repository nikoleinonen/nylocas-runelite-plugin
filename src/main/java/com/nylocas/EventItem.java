package com.nylocas;

import com.google.gson.annotations.SerializedName;
import java.util.List;

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
