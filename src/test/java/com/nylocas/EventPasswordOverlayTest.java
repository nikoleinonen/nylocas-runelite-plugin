package com.nylocas;

import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventPasswordOverlayTest
{
	@Test
	public void formatsUtcTime()
	{
		assertEquals("16/10/2026 20:36 UTC", EventPasswordOverlay.utcTime(Instant.parse("2026-10-16T20:36:00Z")));
		assertEquals("16/10/2026 20:36 UTC", EventPasswordOverlay.utcTime(Instant.parse("2026-10-16T20:36:59Z")));
		assertEquals("05/01/2026 03:04 UTC", EventPasswordOverlay.utcTime(Instant.parse("2026-01-05T03:04:00Z")));
	}
}
