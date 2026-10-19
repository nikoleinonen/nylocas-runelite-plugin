package com.nylocas;

final class ProofMessages
{
	static final String MANUAL = "NCE - Failed to submit proof automatically. Please submit it manually.";
	static final String TILE_COMPLETE = "NCE - Failed to submit proof automatically, tile is completed";
	static final String RATE_LIMITED = "NCE - Rate limited, if you think this appeared with normal use, report it on github or discord.";
	static final String EVENT_ENDED = "NCE - The event has ended.";

	private ProofMessages()
	{
	}

	static boolean endsEvent(String error, boolean afterEnd)
	{
		if ("event has ended".equals(error))
		{
			return true;
		}
		return afterEnd && "unauthorized".equals(error);
	}

	static String line(String error)
	{
		if (error == null)
		{
			return MANUAL;
		}
		switch (error)
		{
			case "event has not started":
			case "row is locked":
			case "event has ended":
				return null;
			case "tile complete":
				return TILE_COMPLETE;
			case "rate limited":
				return RATE_LIMITED;
			default:
				return MANUAL;
		}
	}
}
