package com.nylocas;

import java.awt.Color;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongPredicate;
import java.util.function.Supplier;
import net.runelite.client.util.Text;

final class WebsocketFrames
{
	private final NylocasConfig config;
	private final BooleanSupplier running;
	private final Supplier<EventPanel> panel;
	private final Supplier<EventBoardClient> board;
	private final Consumer<String> chat;
	private final LongPredicate announced;

	WebsocketFrames(NylocasConfig config, BooleanSupplier running, Supplier<EventPanel> panel, Supplier<EventBoardClient> board, Consumer<String> chat, LongPredicate announced)
	{
		this.config = config;
		this.running = running;
		this.panel = panel;
		this.board = board;
		this.chat = chat;
		this.announced = announced;
	}

	void onFrame(EventWebsocket.Frame frame)
	{
		if (!running.getAsBoolean() || frame == null || frame.type == null)
		{
			return;
		}
		if ("progress".equals(frame.type))
		{
			boolean complete = Boolean.TRUE.equals(frame.complete);
			if (frame.approval && config.ownTeamApprovals())
			{
				chat.accept(NotificationText.approved(approvalTemplate(complete), teamName(), tileTitle(frame.tileId), accentColor(true), config.tileColor()));
			}
			else if ("reject".equals(frame.action) || "delete".equals(frame.action))
			{
				if (config.receiveOwnRejections())
				{
					chatReview(frame, true);
				}
			}
			EventPanel current = panel.get();
			if (current != null)
			{
				current.applyProgress(frame.tileId, frame.item, frame.approved, frame.pending, complete);
				if (frame.openRow != null)
				{
					current.noteOpenRow(frame.openRow);
				}
			}
			EventBoardClient stored = board.get();
			if (stored != null)
			{
				stored.noteProgress(frame.tileId, frame.item, frame.approved, frame.pending, complete);
			}
			return;
		}
		if ("approved".equals(frame.type))
		{
			String team = frame.teamName == null ? "" : frame.teamName;
			boolean complete = Boolean.TRUE.equals(frame.complete);
			chat.accept(NotificationText.approved(approvalTemplate(complete), team, enemyTileTitle(frame), accentColor(false), config.tileColor()));
			return;
		}
		if ("dropped".equals(frame.type))
		{
			if (announced.test(frame.id))
			{
				return;
			}
			String team = frame.teamName == null ? "" : frame.teamName;
			boolean own = !team.isEmpty() && team.equals(teamName());
			if (own && !config.eventDrop())
			{
				return;
			}
			chat.accept(NotificationText.drop(NotificationText.TEAM_DROP, team, itemLabel(frame), enemyTileTitle(frame), accentColor(own), config.dropColor(), config.tileColor()));
			return;
		}
		if ("rejected".equals(frame.type) || "deleted".equals(frame.type))
		{
			chatReview(frame, false);
			return;
		}
		if ("code".equals(frame.type))
		{
			EventBoardClient stored = board.get();
			if (stored != null)
			{
				stored.noteEventCode(frame.eventCode);
			}
			return;
		}
		if ("rank".equals(frame.type))
		{
			if (config.firstPlaceChanges())
			{
				boolean winner = Boolean.TRUE.equals(frame.winner);
				chat.accept(NotificationText.rank(rankTemplate(frame.leader, winner), frame.leader, accentColor(ownLeader(frame.leader))));
			}
			EventPanel current = panel.get();
			if (current != null)
			{
				current.showPlace(frame.place);
			}
		}
	}

	private void chatReview(EventWebsocket.Frame frame, boolean own)
	{
		String tile = own ? tileTitle(frame.tileId) : enemyTileTitle(frame);
		String team = own ? teamName() : (frame.teamName == null ? "" : frame.teamName);
		Color teamColor = accentColor(own);
		if (own ? "reject".equals(frame.action) : "rejected".equals(frame.type))
		{
			chat.accept(NotificationText.rejected(rejectionTemplate(frame.rejectionReason), team, tile, frame.rejectionReason, teamColor, config.tileColor(), config.rejectionColor()));
			return;
		}
		if (own ? "delete".equals(frame.action) : "deleted".equals(frame.type))
		{
			chat.accept(NotificationText.deleted(NotificationText.DELETED, team, tile, teamColor, config.tileColor()));
		}
	}

	private String approvalTemplate(boolean complete)
	{
		return complete ? NotificationText.APPROVED_COMPLETE : NotificationText.APPROVED;
	}

	private String rankTemplate(String leader, boolean winner)
	{
		if (leader == null || leader.isEmpty())
		{
			return NotificationText.RANK_TIE;
		}
		if (winner)
		{
			return NotificationText.RANK_WIN;
		}
		return NotificationText.RANK_LEAD;
	}

	private String rejectionTemplate(String reason)
	{
		if (reason == null || Text.removeTags(reason).isEmpty())
		{
			return NotificationText.REJECTED;
		}
		return NotificationText.REJECTED_REASON;
	}

	private String enemyTileTitle(EventWebsocket.Frame frame)
	{
		String local = tileTitle(frame.tileId);
		if (!local.isEmpty())
		{
			return local;
		}
		if (frame.title != null && !frame.title.isEmpty())
		{
			return frame.title;
		}
		return "";
	}

	private String tileTitle(long tileId)
	{
		EventPanel current = panel.get();
		if (current == null)
		{
			return "";
		}
		return current.tileTitle(tileId);
	}

	private String itemLabel(EventWebsocket.Frame frame)
	{
		EventPanel current = panel.get();
		if (current != null)
		{
			return current.itemLabel(frame.tileId, frame.item);
		}
		return TileText.catalogName(frame.item);
	}

	private Color accentColor(boolean own)
	{
		return own ? config.ownTeamColor() : config.enemyTeamColor();
	}

	private boolean ownLeader(String leader)
	{
		if (leader == null || leader.isEmpty())
		{
			return false;
		}
		EventBoardClient stored = board.get();
		if (stored == null)
		{
			return false;
		}
		String ours = stored.teamName();
		return ours != null && !ours.isEmpty() && ours.equals(leader);
	}

	private String teamName()
	{
		EventBoardClient stored = board.get();
		return stored == null || stored.teamName() == null ? "" : stored.teamName();
	}
}
