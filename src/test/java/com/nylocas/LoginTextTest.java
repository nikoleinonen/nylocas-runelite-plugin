package com.nylocas;

import java.awt.Color;
import net.runelite.client.util.ColorUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LoginTextTest
{
	@Test
	public void writesLoginLinesInRed()
	{
		assertEquals(ColorUtil.wrapWithColorTag("Connected to Nylocas event Summer Bingo as Blue", Color.RED), LoginText.connected("Summer Bingo", "Blue"));
		assertEquals(ColorUtil.wrapWithColorTag("Fetched Nylocas event Summer Bingo as Blue", Color.RED), LoginText.fetched("Summer Bingo", "Blue"));
		assertEquals(ColorUtil.wrapWithColorTag("Reconnected to Nylocas event Summer Bingo as Blue", Color.RED), LoginText.reconnected("Summer Bingo", "Blue"));
		assertEquals(
			ColorUtil.wrapWithColorTag("Connect to the Nylocas event via the side panel. Clear the team key to not get this message again.", Color.RED),
			LoginText.reminder(true));
		assertEquals(
			ColorUtil.wrapWithColorTag("Fetch the Nylocas event via the side panel. Clear the team key to not get this message again.", Color.RED),
			LoginText.reminder(false));
	}

	@Test
	public void stripsTagsFromNames()
	{
		assertEquals(
			ColorUtil.wrapWithColorTag("Connected to Nylocas event Summer Bingo as Blue", Color.RED),
			LoginText.connected("Summer Bingo</col>", "<col=ffffff>Blue"));
	}
}
