package com.nylocas;

import java.time.Instant;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class EventClockTest
{
	@Test
	public void parsesUtc()
	{
		assertEquals(Instant.parse("2026-11-01T18:00:00Z"), EventClock.parse("2026-11-01T18:00:00Z"));
		assertNull(EventClock.parse(null));
		assertNull(EventClock.parse(""));
		assertNull(EventClock.parse("tomorrow"));
	}

	@Test
	public void windowIsStartInclusiveAndEndExclusive()
	{
		Instant starts = Instant.parse("2026-11-01T12:00:00Z");
		Instant ends = Instant.parse("2026-11-01T18:00:00Z");
		assertTrue(EventClock.beforeStart(starts, starts.minusSeconds(1)));
		assertFalse(EventClock.beforeStart(starts, starts));
		assertFalse(EventClock.afterEnd(ends, ends.minusSeconds(1)));
		assertTrue(EventClock.afterEnd(ends, ends));
		assertFalse(EventClock.beforeStart(null, starts));
		assertFalse(EventClock.afterEnd(null, ends));
	}
}
