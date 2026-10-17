package com.nylocas;

import java.time.Instant;

final class EventClock
{
	private EventClock()
	{
	}

	static Instant parse(String raw)
	{
		if (raw == null || raw.isEmpty())
		{
			return null;
		}
		try
		{
			return Instant.parse(raw);
		}
		catch (RuntimeException ex)
		{
			return null;
		}
	}

	static boolean beforeStart(Instant startsAt, Instant now)
	{
		return startsAt != null && now.isBefore(startsAt);
	}

	static boolean afterEnd(Instant endsAt, Instant now)
	{
		return endsAt != null && !now.isBefore(endsAt);
	}
}
