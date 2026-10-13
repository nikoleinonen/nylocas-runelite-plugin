package com.nylocas;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.runelite.client.util.Filepath;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScreenshotFilesTest
{
	@Test
	public void eventNameIsTheFolder() throws Exception
	{
		assertEquals("Autumn Bingo 7", ScreenshotFiles.folderName("Autumn Bingo 7"));
		assertEquals("2026-10-13_21-07-12_000 Crystal weapon seed.png", ScreenshotFiles.fileName("2026-10-13_21-07-12_000", List.of("Crystal weapon seed")));

		Path root = Files.createTempDirectory("nylocas-shots");
		Filepath folder = Filepath.Unchecked.getRooted(root).joinSegment(ScreenshotFiles.folderName("Autumn Bingo 7"));
		folder.joinSegment(ScreenshotFiles.fileName("2026-10-13_21-07-12_000", List.of("Crystal weapon seed", "Bandos hilt")));
		folder.deleteIfExists();
		root.toFile().delete();
	}

	@Test
	public void folderNameDropsCharactersAPathCannotUse()
	{
		assertEquals("Autumn Bingo 7", ScreenshotFiles.folderName("Autumn: Bingo 7"));
		assertEquals("event", ScreenshotFiles.folderName("   "));
		assertEquals("con_", ScreenshotFiles.folderName("con"));
	}
}
