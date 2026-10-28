package com.nylocas;

import java.awt.Color;
import net.runelite.client.ui.ColorScheme;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EventPanelRankTest
{
	@Test
	public void colorsThePodium()
	{
		assertEquals(new Color(0xe4c56a), EventPanel.rankColor("WINNER"));
		assertEquals(new Color(0xe4c56a), EventPanel.rankColor("1st"));
		assertEquals(new Color(0xc5c9d0), EventPanel.rankColor("2nd"));
		assertEquals(new Color(0xc4894a), EventPanel.rankColor("3rd"));
		assertEquals(ColorScheme.TEXT_COLOR, EventPanel.rankColor("4th"));
	}

	@Test
	public void fetchStatus()
	{
		assertEquals("The event has ended", EventPanel.fetchStatus("event has ended"));
		assertEquals("No event found for the provided team key", EventPanel.fetchStatus("unauthorized"));
		assertEquals("No event found for the provided team key", EventPanel.fetchStatus("not found"));
		assertEquals("Could not fetch event", EventPanel.fetchStatus("Could not fetch event"));
	}
}
