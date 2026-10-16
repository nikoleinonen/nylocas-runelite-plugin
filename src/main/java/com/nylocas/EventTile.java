package com.nylocas;

import java.util.List;

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
