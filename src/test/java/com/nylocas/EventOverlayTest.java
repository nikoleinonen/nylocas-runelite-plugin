package com.nylocas;

import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventOverlayTest
{
	@Test
	public void formatsUtcTime()
	{
		assertEquals("16/10/2026 20:36 UTC", EventOverlay.utcTime(Instant.parse("2026-10-16T20:36:00Z")));
		assertEquals("16/10/2026 20:36 UTC", EventOverlay.utcTime(Instant.parse("2026-10-16T20:36:59Z")));
		assertEquals("05/01/2026 03:04 UTC", EventOverlay.utcTime(Instant.parse("2026-01-05T03:04:00Z")));
	}

	@Test
	public void prefersFetchedCode()
	{
		assertEquals("Banana5", EventOverlay.label(true, true, "Banana5", "Manual", null, null));
		assertEquals("Banana5", EventOverlay.label(true, true, " Banana5 ", "", null, null));
		assertEquals("Manual", EventOverlay.label(true, true, "", " Manual ", null, null));
		assertEquals("Manual", EventOverlay.label(true, true, null, "Manual", null, null));
		assertEquals("", EventOverlay.label(true, true, " ", " ", null, null));
		assertEquals("", EventOverlay.label(false, false, "", " ", null, null));
		assertEquals("Manual", EventOverlay.label(false, false, "", "Manual", null, null));
		assertEquals("", EventOverlay.label(true, false, "Banana5", "Manual", null, null));
	}

	@Test
	public void countsDownToStart()
	{
		Instant start = Instant.parse("2026-10-18T18:00:00Z");
		assertEquals("The event starts in: 2d 3h 4m 5s", EventOverlay.label(true, false, "Banana5", "Manual", start, start.minusSeconds(2 * 86_400 + 3 * 3_600 + 4 * 60 + 5)));
		assertEquals("The event starts in: 1d 0s", EventOverlay.label(true, false, "", "", start, start.minusSeconds(86_400)));
		assertEquals("The event starts in: 3h 5s", EventOverlay.label(true, false, "", "", start, start.minusSeconds(3 * 3_600 + 5)));
		assertEquals("The event starts in: 1m 30s", EventOverlay.label(true, false, "", "", start, start.minusSeconds(90)));
		assertEquals("The event starts in: 45s", EventOverlay.label(true, false, "", "", start, start.minusSeconds(45)));
		assertEquals("Refresh the event to get the code", EventOverlay.label(true, false, "", "", start, start));
		assertEquals("Refresh the event to get the code", EventOverlay.label(true, false, "Banana5", "Manual", start, start.plusSeconds(5)));
	}
}
