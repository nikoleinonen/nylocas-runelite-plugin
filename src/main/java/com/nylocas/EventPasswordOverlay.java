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

final class EventPasswordOverlay extends OverlayPanel
{
	private static final String NOT_STARTED = "Event has not started";
	private static final DateTimeFormatter UTC_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

	private final NylocasConfig config;
	private final NylocasPlugin plugin;

	EventPasswordOverlay(NylocasConfig config, NylocasPlugin plugin)
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
		if (!plugin.eventLoaded())
		{
			return null;
		}
		String label = plugin.eventStarted() ? plugin.eventPassword() : NOT_STARTED;
		if (label.isEmpty())
		{
			return null;
		}
		String time = utcTime(Instant.now());
		panelComponent.setPreferredSize(new Dimension(graphics.getFontMetrics().stringWidth(label + " " + time) + 10, 0));
		panelComponent.getChildren().add(LineComponent.builder()
			.left(label)
			.leftColor(config.passwordColor())
			.right(time)
			.rightColor(config.timeColor())
			.build());
		return super.render(graphics);
	}

	static String utcTime(Instant instant)
	{
		return UTC_TIME.format(instant) + " UTC";
	}
}
