package com.nylocas;

import com.google.gson.annotations.SerializedName;
import java.util.List;

final class EventSource
{
	String id;

	@SerializedName("name")
	String displayName;

	@SerializedName("runelite_names")
	List<String> runeliteNames;
}
