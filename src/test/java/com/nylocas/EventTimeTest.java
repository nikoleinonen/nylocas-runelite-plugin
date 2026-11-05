package com.nylocas;

import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EventTimeTest
{
	@Test
	public void parsesUtc()
	{
		assertEquals(Instant.parse("2026-11-01T18:00:00Z"), EventTime.parse("2026-11-01T18:00:00Z"));
		assertNull(EventTime.parse(null));
		assertNull(EventTime.parse(""));
		assertNull(EventTime.parse("tomorrow"));
	}

	@Test
	public void eventWindow()
	{
		Instant starts = Instant.parse("2026-11-01T12:00:00Z");
		Instant ends = Instant.parse("2026-11-01T18:00:00Z");
		assertTrue(EventTime.beforeStart(starts, starts.minusSeconds(1)));
		assertFalse(EventTime.beforeStart(starts, starts));
		assertFalse(EventTime.afterEnd(ends, ends.minusSeconds(1)));
		assertTrue(EventTime.afterEnd(ends, ends));
		assertFalse(EventTime.beforeStart(null, starts));
		assertFalse(EventTime.afterEnd(null, ends));
	}
}
