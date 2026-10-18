package com.nylocas;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;

final class EventCodeOverlay extends OverlayPanel
{
	private static final String STARTS_IN = "The event starts in: ";
	private static final String REFRESH = "Refresh the event to get the code";
	private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

	private final NylocasConfig config;
	private final NylocasPlugin plugin;

	EventCodeOverlay(NylocasConfig config, NylocasPlugin plugin)
	{
		this.config = config;
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_CENTER);
		getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Nylocas overlay"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.displayOverlay())
		{
			return null;
		}
		boolean countdown = plugin.eventLoaded() && !plugin.eventStarted();
		String label = label(plugin.eventLoaded(), plugin.eventStarted(), plugin.eventCode(), config.eventCode(), plugin.eventStartsAt(), Instant.now());
		String time = utcTime(Instant.now());
		if (countdown && label.isEmpty())
		{
			return null;
		}
		String shown;
		if (countdown)
		{
			shown = label;
		}
		else if (label.isEmpty())
		{
			shown = time;
		}
		else
		{
			shown = label + " " + time;
		}
		panelComponent.setPreferredSize(new Dimension(graphics.getFontMetrics().stringWidth(shown) + 10, 0));
		if (countdown)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(label)
				.leftColor(config.codeColor())
				.build());
		}
		else if (label.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(time)
				.leftColor(config.timeColor())
				.build());
		}
		else
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(label)
				.leftColor(config.codeColor())
				.right(time)
				.rightColor(config.timeColor())
				.build());
		}
		return super.render(graphics);
	}

	static String utcTime(Instant instant)
	{
		return UTC_TIME.format(instant) + " UTC";
	}

	static String label(boolean loaded, boolean started, String fetched, String manual, Instant startsAt, Instant now)
	{
		if (loaded && !started)
		{
			return startsIn(startsAt, now);
		}
		String fromEvent = text(fetched);
		if (!fromEvent.isEmpty())
		{
			return fromEvent;
		}
		return text(manual);
	}

	static String startsIn(Instant startsAt, Instant now)
	{
		if (startsAt == null || now == null)
		{
			return "";
		}
		if (!now.isBefore(startsAt))
		{
			return REFRESH;
		}
		return STARTS_IN + countdown(Duration.between(now, startsAt).getSeconds());
	}

	static String countdown(long seconds)
	{
		long days = seconds / 86_400;
		long hours = (seconds % 86_400) / 3_600;
		long minutes = (seconds % 3_600) / 60;
		long remainingSeconds = seconds % 60;
		StringBuilder text = new StringBuilder();
		if (days > 0)
		{
			text.append(days).append('d');
		}
		if (hours > 0)
		{
			if (text.length() > 0)
			{
				text.append(' ');
			}
			text.append(hours).append('h');
		}
		if (minutes > 0)
		{
			if (text.length() > 0)
			{
				text.append(' ');
			}
			text.append(minutes).append('m');
		}
		if (text.length() > 0)
		{
			text.append(' ');
		}
		text.append(remainingSeconds).append('s');
		return text.toString();
	}

	private static String text(String value)
	{
		return value == null ? "" : value.trim();
	}
}
