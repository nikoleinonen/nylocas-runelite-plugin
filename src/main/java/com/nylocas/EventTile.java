package com.nylocas;

import com.google.gson.annotations.SerializedName;
import java.util.List;

final class EventTile
{
	long id;
	int row;
	int col;
	String title;

	@SerializedName("icon_item")
	String iconItem;

	@SerializedName("image_url")
	String imageUrl;

	@SerializedName("image_fit")
	String imageFit;

	@SerializedName("icon_count")
	Integer iconCount;

	boolean complete;
	boolean partial;
	int need;
	boolean pool;
	List<EventItem> items;
}
