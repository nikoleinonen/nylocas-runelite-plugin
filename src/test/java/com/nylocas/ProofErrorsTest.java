package com.nylocas;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProofErrorsTest
{
	@Test
	public void noLine()
	{
		assertNull(ProofErrors.line("event has not started"));
		assertNull(ProofErrors.line("row is locked"));
		assertFalse(ProofErrors.endsEvent("event has not started", false));
		assertFalse(ProofErrors.endsEvent("row is locked", true));
	}

	@Test
	public void finishedTile()
	{
		assertEquals(ProofErrors.TILE_COMPLETE, ProofErrors.line("tile complete"));
	}

	@Test
	public void manualSubmit()
	{
		String[] errors = {
			"invalid tiles",
			"invalid player",
			"invalid item",
			"invalid source",
			"invalid completions",
			"already exists",
			"not found",
			"invalid image",
			"image too large",
			"invalid submission",
			"invalid body",
			"body too large",
			"request timeout",
			"internal error",
			"unauthorized"
		};
		for (String error : errors)
		{
			assertEquals(error, ProofErrors.MANUAL, ProofErrors.line(error));
		}
	}

	@Test
	public void rateLimit()
	{
		assertEquals(ProofErrors.RATE_LIMITED, ProofErrors.line("rate limited"));
	}

	@Test
	public void eventOver()
	{
		assertTrue(ProofErrors.endsEvent("event has ended", false));
		assertTrue(ProofErrors.endsEvent("unauthorized", true));
		assertFalse(ProofErrors.endsEvent("unauthorized", false));
		assertNull(ProofErrors.line("event has ended"));
	}
}
