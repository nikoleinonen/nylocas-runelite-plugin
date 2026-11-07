package com.nylocas;

import java.awt.Color;
import net.runelite.client.util.ColorUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LoginTextTest
{
	@Test
	public void stripsTags()
	{
		assertEquals(
			ColorUtil.wrapWithColorTag("Connected to Nylocas event Summer Bingo as Blue", Color.RED),
			LoginText.connected("Summer Bingo</col>", "<col=ffffff>Blue"));
	}
}
