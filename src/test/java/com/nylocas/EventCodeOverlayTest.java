package com.nylocas;

import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventCodeOverlayTest
{
	@Test
	public void formatsUtcTime()
	{
		assertEquals("16/10/2026 20:36 UTC", EventCodeOverlay.utcTime(Instant.parse("2026-10-16T20:36:00Z")));
		assertEquals("16/10/2026 20:36 UTC", EventCodeOverlay.utcTime(Instant.parse("2026-10-16T20:36:59Z")));
		assertEquals("05/01/2026 03:04 UTC", EventCodeOverlay.utcTime(Instant.parse("2026-01-05T03:04:00Z")));
	}

	@Test
	public void prefersFetchedCode()
	{
		assertEquals("Banana5", EventCodeOverlay.label(true, true, "Banana5", "Manual"));
		assertEquals("Banana5", EventCodeOverlay.label(true, true, " Banana5 ", ""));
		assertEquals("Manual", EventCodeOverlay.label(true, true, "", " Manual "));
		assertEquals("Manual", EventCodeOverlay.label(true, true, null, "Manual"));
		assertEquals("", EventCodeOverlay.label(true, true, " ", " "));
		assertEquals("", EventCodeOverlay.label(false, false, "", " "));
		assertEquals("Manual", EventCodeOverlay.label(false, false, "", "Manual"));
		assertEquals("The event has not started", EventCodeOverlay.label(true, false, "", "Manual"));
		assertEquals("The event has not started", EventCodeOverlay.label(true, false, "Banana5", "Manual"));
	}
}
