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
		assertEquals(ColorScheme.TEXT_COLOR, EventPanel.rankColor("11th"));
		assertEquals(ColorScheme.TEXT_COLOR, EventPanel.rankColor(null));
		assertEquals(ColorScheme.TEXT_COLOR, EventPanel.rankColor(""));
	}
}
