package com.nylocas;

import java.awt.Dimension;
import java.awt.Graphics2D;
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
	private static final String NOT_STARTED = "The event has not started";
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
		String label = label(plugin.eventLoaded(), plugin.eventStarted(), plugin.eventCode(), config.eventCode());
		String time = utcTime(Instant.now());
		String shown = label.isEmpty() ? time : label + " " + time;
		panelComponent.setPreferredSize(new Dimension(graphics.getFontMetrics().stringWidth(shown) + 10, 0));
		if (label.isEmpty())
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

	static String label(boolean loaded, boolean started, String fetched, String manual)
	{
		if (loaded && !started)
		{
			return NOT_STARTED;
		}
		String fromEvent = text(fetched);
		if (!fromEvent.isEmpty())
		{
			return fromEvent;
		}
		return text(manual);
	}

	private static String text(String value)
	{
		return value == null ? "" : value.trim();
	}
}
