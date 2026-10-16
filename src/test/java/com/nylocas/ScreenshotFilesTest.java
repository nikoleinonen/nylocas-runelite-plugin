package com.nylocas;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScreenshotFilesTest
{
	@Test
	public void eventNameIsTheFolder()
	{
		assertEquals("Autumn Bingo 7", ScreenshotFiles.folderName("Autumn Bingo 7"));
		assertEquals("2026-10-13_21-07-12_000 Crystal weapon seed.png", ScreenshotFiles.fileName("2026-10-13_21-07-12_000", List.of("Crystal weapon seed")));
	}

	@Test
	public void folderNameDropsCharactersAPathCannotUse()
	{
		assertEquals("Autumn Bingo 7", ScreenshotFiles.folderName("Autumn: Bingo 7"));
		assertEquals("event", ScreenshotFiles.folderName("   "));
		assertEquals("con_", ScreenshotFiles.folderName("con"));
	}
}
