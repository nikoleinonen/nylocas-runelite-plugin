package com.nylocas;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProofMessagesTest
{
	@Test
	public void staysQuietBeforeStartAndOnALockedRow()
	{
		assertNull(ProofMessages.line("event has not started"));
		assertNull(ProofMessages.line("row is locked"));
		assertFalse(ProofMessages.endsEvent("event has not started", false));
		assertFalse(ProofMessages.endsEvent("row is locked", true));
	}

	@Test
	public void namesAFinishedTile()
	{
		assertEquals(ProofMessages.TILE_COMPLETE, ProofMessages.line("tile complete"));
	}

	@Test
	public void asksForAManualSubmit()
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
			assertEquals(error, ProofMessages.MANUAL, ProofMessages.line(error));
		}
	}

	@Test
	public void namesRateLimit()
	{
		assertEquals(ProofMessages.RATE_LIMITED, ProofMessages.line("rate limited"));
	}

	@Test
	public void endsTheSessionOnceTheEventIsOver()
	{
		assertTrue(ProofMessages.endsEvent("event has ended", false));
		assertTrue(ProofMessages.endsEvent("unauthorized", true));
		assertFalse(ProofMessages.endsEvent("unauthorized", false));
		assertNull(ProofMessages.line("event has ended"));
	}
}
