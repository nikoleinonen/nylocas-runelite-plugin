package com.nylocas;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScreenshotFilesTest
{
	@Test
	public void pathCharacters()
	{
		assertEquals("Autumn Bingo 7", ScreenshotFiles.folderName("Autumn: Bingo 7"));
		assertEquals("event", ScreenshotFiles.folderName("   "));
		assertEquals("con_", ScreenshotFiles.folderName("con"));
	}
}
