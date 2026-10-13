package com.nylocas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class ScreenshotFiles
{
	private static final Set<String> RESERVED = Set.of(
		"con", "prn", "aux", "nul",
		"com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
		"lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
	);

	private ScreenshotFiles()
	{
	}

	static String folderName(String eventName)
	{
		String cleaned = segment(eventName);
		if (cleaned.isEmpty())
		{
			return "event";
		}
		return cleaned;
	}

	static String fileName(String stamp, List<String> labels)
	{
		StringBuilder name = new StringBuilder(segment(stamp));
		List<String> parts = new ArrayList<>();
		if (labels != null)
		{
			for (String label : labels)
			{
				String cleaned = segment(label);
				if (!cleaned.isEmpty())
				{
					parts.add(cleaned);
				}
			}
		}
		if (!parts.isEmpty())
		{
			if (name.length() > 0)
			{
				name.append(' ');
			}
			name.append(String.join(", ", parts));
		}
		String text = name.toString();
		if (text.length() > 180)
		{
			text = trimTrailing(text.substring(0, 180));
		}
		if (text.isEmpty())
		{
			text = "screenshot";
		}
		return text + ".png";
	}

	private static String segment(String raw)
	{
		if (raw == null)
		{
			return "";
		}
		StringBuilder out = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++)
		{
			char c = raw.charAt(i);
			if (c <= 0x1f || "<>:\"/\\|?*~".indexOf(c) >= 0)
			{
				continue;
			}
			out.append(c);
		}
		String cleaned = trimTrailing(out.toString().trim());
		if (cleaned.isEmpty())
		{
			return "";
		}
		String stem = cleaned;
		int dot = stem.indexOf('.');
		if (dot >= 0)
		{
			stem = stem.substring(0, dot);
		}
		if (RESERVED.contains(stem.toLowerCase(Locale.ROOT).trim()))
		{
			cleaned = cleaned + "_";
		}
		return cleaned;
	}

	private static String trimTrailing(String value)
	{
		int end = value.length();
		while (end > 0)
		{
			char c = value.charAt(end - 1);
			if (c != ' ' && c != '.')
			{
				break;
			}
			end--;
		}
		return value.substring(0, end);
	}
}
