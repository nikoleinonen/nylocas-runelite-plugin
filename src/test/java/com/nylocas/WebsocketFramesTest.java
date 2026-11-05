package com.nylocas;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WebsocketFramesTest
{
	@Test
	public void ownApprovalChatsWhenEnabled()
	{
		List<String> lines = new ArrayList<>();
		NylocasConfig config = new NylocasConfig()
		{
			@Override
			public boolean ownTeamApprovals()
			{
				return true;
			}
		};
		EventWebsocket.Frame frame = new EventWebsocket.Frame();
		frame.type = "progress";
		frame.approval = true;
		frame.tileId = 4;
		frames(config, lines, id -> false).onFrame(frame);
		assertEquals(
			List.of(NotificationText.approved(NotificationText.APPROVED, "", "", config.ownTeamColor(), config.tileColor())),
			lines);
	}

	@Test
	public void ownApprovalStaysQuietWhenDisabled()
	{
		List<String> lines = new ArrayList<>();
		EventWebsocket.Frame frame = new EventWebsocket.Frame();
		frame.type = "progress";
		frame.approval = true;
		frames(new NylocasConfig()
		{
		}, lines, id -> false).onFrame(frame);
		assertEquals(List.of(), lines);
	}

	@Test
	public void announcedDropIsSilent()
	{
		List<String> lines = new ArrayList<>();
		EventWebsocket.Frame frame = new EventWebsocket.Frame();
		frame.type = "dropped";
		frame.id = 9;
		frame.teamName = "Blue";
		frames(new NylocasConfig()
		{
		}, lines, id -> id == 9).onFrame(frame);
		assertEquals(List.of(), lines);
	}

	@Test
	public void enemyDropUsesFrameTitle()
	{
		List<String> lines = new ArrayList<>();
		NylocasConfig config = new NylocasConfig()
		{
		};
		EventWebsocket.Frame frame = new EventWebsocket.Frame();
		frame.type = "dropped";
		frame.id = 3;
		frame.teamName = "Red";
		frame.item = "bandos_hilt";
		frame.title = "Bandos";
		frames(config, lines, id -> false).onFrame(frame);
		assertEquals(
			List.of(NotificationText.drop(NotificationText.TEAM_DROP, "Red", "Bandos hilt", "Bandos", config.enemyTeamColor(), config.dropColor(), config.tileColor())),
			lines);
	}

	@Test
	public void ignoresFramesWhileStopped()
	{
		List<String> lines = new ArrayList<>();
		NylocasConfig config = new NylocasConfig()
		{
			@Override
			public boolean firstPlaceChanges()
			{
				return true;
			}
		};
		EventWebsocket.Frame frame = new EventWebsocket.Frame();
		frame.type = "rank";
		frame.leader = "Blue";
		new WebsocketFrames(config, () -> false, () -> null, () -> null, lines::add, id -> false).onFrame(frame);
		assertEquals(List.of(), lines);
	}

	private static WebsocketFrames frames(NylocasConfig config, List<String> lines, LongPredicate announced)
	{
		return new WebsocketFrames(config, () -> true, () -> null, () -> null, lines::add, announced);
	}
}
