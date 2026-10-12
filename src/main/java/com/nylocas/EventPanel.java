package com.nylocas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;

final class EventPanel extends PluginPanel
{
	private static final int TEXT_WIDTH = 190;
	private static final int CELL = 36;
	private static final Border CELL_BORDER = BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR, 1);
	private static final Border SELECTED_BORDER = BorderFactory.createLineBorder(ColorScheme.TEXT_COLOR, 1);
	private static final Color COMPLETE_COLOR = new Color(28, 92, 42);
	private static final Color PARTIAL_COLOR = new Color(122, 78, 18);
	private static final Color SOURCE_TEXT = new Color(160, 160, 160);
	private static final Color PENDING_TEXT = new Color(214, 176, 92);
	private static final Color PROGRESS_TEXT = new Color(126, 196, 140);
	private static final int COOLDOWN_MS = 60_000;
	private static final String FETCH_LABEL = "Fetch event";

	private final NylocasPlugin plugin;
	private final JPasswordField keyField = new JPasswordField();
	private final JButton fetchButton = new JButton(FETCH_LABEL);
	private final JLabel status = new JLabel(" ");
	private final JPanel gridHost = new JPanel();
	private final JPanel drops = new JPanel();
	private final Map<JPanel, EventTile> cells = new HashMap<>();
	private Map<Integer, String> itemNames = Map.of();
	private Map<Integer, AsyncBufferedImage> itemImages = Map.of();
	private JPanel selectedCell;
	private boolean fetching;
	private boolean suppressKeySave;
	private boolean keyEdited;
	private boolean keyLoaded;
	private volatile boolean stopped;
	private long cooldownUntil;
	private Timer cooldownTimer;

	EventPanel(NylocasPlugin plugin)
	{
		super();
		this.plugin = plugin;

		keyField.setEchoChar('\u2022');

		JPanel form = new JPanel(new GridLayout(0, 1, 0, 8));
		form.setBackground(ColorScheme.DARK_GRAY_COLOR);
		form.add(new JLabel("Team key"));
		form.add(keyField);
		form.add(fetchButton);
		form.add(status);
		add(form);

		gridHost.setLayout(new BoxLayout(gridHost, BoxLayout.Y_AXIS));
		gridHost.setBackground(ColorScheme.DARK_GRAY_COLOR);
		gridHost.setBorder(new EmptyBorder(8, 0, 0, 0));
		add(gridHost);

		drops.setLayout(new BoxLayout(drops, BoxLayout.Y_AXIS));
		drops.setBackground(ColorScheme.DARK_GRAY_COLOR);
		drops.setBorder(new EmptyBorder(8, 0, 0, 0));
		add(drops);

		fetchButton.setEnabled(false);
		fetchButton.addActionListener(event -> fetch());
		keyField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent event)
			{
				keyChanged();
			}

			@Override
			public void removeUpdate(DocumentEvent event)
			{
				keyChanged();
			}

			@Override
			public void changedUpdate(DocumentEvent event)
			{
				keyChanged();
			}
		});

		refreshButton();
	}

	void showSavedKey(String key)
	{
		if (stopped)
		{
			return;
		}
		keyLoaded = true;
		if (!keyEdited)
		{
			suppressKeySave = true;
			keyField.setText(key == null ? "" : key);
			suppressKeySave = false;
			refreshButton();
			return;
		}
		plugin.queueTeamKey(currentKey());
	}

	private void keyChanged()
	{
		refreshButton();
		if (suppressKeySave)
		{
			return;
		}
		keyEdited = true;
		if (keyLoaded)
		{
			plugin.queueTeamKey(currentKey());
		}
	}

	private String currentKey()
	{
		return new String(keyField.getPassword()).trim();
	}

	private void fetch()
	{
		String key = currentKey();
		if (key.isEmpty() || fetching || coolingDown())
		{
			return;
		}
		plugin.flushTeamKey(key);
		fetching = true;
		fetchButton.setEnabled(false);
		status.setText("Fetching...");
		clearBoard();
		if (!plugin.fetchEvent(key, this))
		{
			return;
		}
		startCooldown();
	}

	void stop()
	{
		stopped = true;
		if (cooldownTimer != null)
		{
			cooldownTimer.stop();
		}
	}

	private void startCooldown()
	{
		cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS;
		if (cooldownTimer == null)
		{
			cooldownTimer = new Timer(1000, event -> tickCooldown());
		}
		cooldownTimer.setInitialDelay(0);
		cooldownTimer.restart();
	}

	private void tickCooldown()
	{
		long left = cooldownUntil - System.currentTimeMillis();
		if (left <= 0)
		{
			cooldownUntil = 0;
			fetchButton.setText(FETCH_LABEL);
			cooldownTimer.stop();
		}
		else
		{
			int seconds = (int) Math.ceil(left / 1000.0);
			fetchButton.setText(FETCH_LABEL + " (" + seconds + "s)");
		}
		refreshButton();
	}

	private boolean coolingDown()
	{
		return System.currentTimeMillis() < cooldownUntil;
	}

	void showStatus(String message)
	{
		fetching = false;
		status.setText(message);
		clearBoard();
		refreshButton();
	}

	void showBoard(EventBoard board, Map<Integer, String> names, Map<Integer, AsyncBufferedImage> images)
	{
		fetching = false;
		status.setText(teamLabel(board));
		itemNames = names == null ? Map.of() : names;
		itemImages = images == null ? Map.of() : images;
		selectedCell = null;
		cells.clear();
		gridHost.removeAll();
		drops.removeAll();
		int rows = board == null ? 0 : board.rows;
		int cols = board == null ? 0 : board.cols;
		if (rows <= 0 || cols <= 0)
		{
			drops.add(wrappingLabel("No board on this event", false));
		}
		else
		{
			JPanel grid = new JPanel(new GridLayout(rows, cols, 2, 2));
			grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
			int width = PluginPanel.PANEL_WIDTH - PluginPanel.BORDER_OFFSET * 2;
			int height = CELL * rows + 2 * (rows - 1);
			grid.setPreferredSize(new Dimension(width, height));
			grid.setMinimumSize(grid.getPreferredSize());
			grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
			grid.setAlignmentX(LEFT_ALIGNMENT);
			int openRow = board.openRow;
			for (int row = 0; row < rows; row++)
			{
				for (int col = 0; col < cols; col++)
				{
					EventTile tile = tileAt(board.tiles, row, col);
					if (row < openRow || tile == null)
					{
						grid.add(lockedCell(tile == null && row >= openRow));
						continue;
					}
					grid.add(tileCell(tile));
				}
			}
			gridHost.add(grid);
			drops.add(wrappingLabel("Select a tile", false));
		}
		gridHost.revalidate();
		gridHost.repaint();
		drops.revalidate();
		drops.repaint();
		revalidate();
		repaint();
		refreshButton();
	}

	private JPanel tileCell(EventTile tile)
	{
		JPanel cell = new JPanel(new GridLayout(1, 1));
		cell.setBackground(tileColor(tile));
		cell.setBorder(CELL_BORDER);
		cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		cell.setToolTipText(tileTooltip(tile));
		JLabel label = new JLabel();
		label.setHorizontalAlignment(JLabel.CENTER);
		AsyncBufferedImage image = itemImage(firstItemId(tile));
		if (image != null)
		{
			image.addTo(label);
		}
		cell.add(label);
		cells.put(cell, tile);
		cell.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent event)
			{
				select(cell);
			}
		});
		return cell;
	}

	private JPanel lockedCell(boolean missing)
	{
		JPanel cell = new JPanel(new GridLayout(1, 1));
		cell.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		cell.setBorder(CELL_BORDER);
		cell.setToolTipText(missing ? "Empty" : "Locked");
		return cell;
	}

	private void select(JPanel cell)
	{
		if (selectedCell != null)
		{
			selectedCell.setBorder(CELL_BORDER);
		}
		selectedCell = cell;
		cell.setBorder(SELECTED_BORDER);
		EventTile tile = cells.get(cell);
		drops.removeAll();
		if (tile == null)
		{
			drops.revalidate();
			drops.repaint();
			return;
		}
		drops.add(wrappingLabel(tileTitle(tile), true));
		drops.add(statusLabel(tile));
		String requirement = AllowedDrops.requirement(tile);
		if (!requirement.isEmpty())
		{
			drops.add(Box.createVerticalStrut(6));
			drops.add(wrappingLabel(requirement, false));
		}
		if (tile.items != null)
		{
			for (EventItem item : tile.items)
			{
				if (item == null)
				{
					continue;
				}
				drops.add(Box.createVerticalStrut(4));
				drops.add(itemCard(tile, item));
			}
		}
		drops.revalidate();
		drops.repaint();
		revalidate();
		repaint();
	}

	private void clearBoard()
	{
		selectedCell = null;
		cells.clear();
		itemNames = Map.of();
		itemImages = Map.of();
		gridHost.removeAll();
		drops.removeAll();
		gridHost.revalidate();
		gridHost.repaint();
		drops.revalidate();
		drops.repaint();
	}

	private static String teamLabel(EventBoard board)
	{
		if (board == null || board.teamName == null || board.teamName.isEmpty())
		{
			return " ";
		}
		return "<html><b>" + escape(board.teamName) + "</b></html>";
	}

	private static EventTile tileAt(List<EventTile> tiles, int row, int col)
	{
		if (tiles == null)
		{
			return null;
		}
		for (EventTile tile : tiles)
		{
			if (tile != null && tile.row == row && tile.col == col)
			{
				return tile;
			}
		}
		return null;
	}

	private static Color tileColor(EventTile tile)
	{
		if (tile.complete)
		{
			return COMPLETE_COLOR;
		}
		if (tile.partial)
		{
			return PARTIAL_COLOR;
		}
		return ColorScheme.MEDIUM_GRAY_COLOR;
	}

	private static String tileTitle(EventTile tile)
	{
		if (tile.title == null || tile.title.isEmpty())
		{
			return "Tile " + tile.id;
		}
		return tile.title;
	}

	private JPanel itemCard(EventTile tile, EventItem item)
	{
		String bosses = bossTip(item);
		JPanel card = new FitWidth(new BorderLayout(6, 0));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR, 1),
			new EmptyBorder(4, 4, 4, 6)
		));
		card.setAlignmentX(LEFT_ALIGNMENT);
		hover(card, bosses);

		Integer itemId = firstId(item.runeliteIds);
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(32, 32));
		icon.setHorizontalAlignment(JLabel.CENTER);
		icon.setVerticalAlignment(JLabel.CENTER);
		AsyncBufferedImage image = itemImage(itemId);
		if (image != null)
		{
			image.addTo(icon);
		}
		hover(icon, bosses);
		card.add(icon, BorderLayout.WEST);

		JLabel name = new JLabel(itemLabel(item, itemId));
		name.setVerticalAlignment(JLabel.CENTER);
		name.setForeground(ColorScheme.TEXT_COLOR);
		hover(name, bosses);
		card.add(name, BorderLayout.CENTER);

		JPanel count = countColumn(tile, item);
		hover(count, bosses);
		card.add(count, BorderLayout.EAST);
		return card;
	}

	private JPanel countColumn(EventTile tile, EventItem item)
	{
		JLabel count = new JLabel(AllowedDrops.itemFraction(tile, item));
		count.setForeground(fractionColor(item));
		count.setHorizontalAlignment(JLabel.RIGHT);
		String pending = AllowedDrops.pendingText(item);
		if (pending.isEmpty())
		{
			count.setBorder(new EmptyBorder(0, 4, 0, 0));
			JPanel holder = new JPanel(new BorderLayout());
			holder.setOpaque(false);
			holder.add(count, BorderLayout.CENTER);
			return holder;
		}
		JLabel waiting = new JLabel(pending);
		waiting.setForeground(PENDING_TEXT);
		waiting.setHorizontalAlignment(JLabel.RIGHT);
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setOpaque(false);
		column.setBorder(new EmptyBorder(0, 4, 0, 0));
		count.setAlignmentX(RIGHT_ALIGNMENT);
		waiting.setAlignmentX(RIGHT_ALIGNMENT);
		column.add(count);
		column.add(waiting);
		return column;
	}

	private static String bossTip(EventItem item)
	{
		String sources = AllowedDrops.sourcesText(item);
		if (sources.isEmpty())
		{
			return "";
		}
		return "Allowed from " + sources;
	}

	private static void hover(javax.swing.JComponent component, String tip)
	{
		if (tip != null && !tip.isEmpty())
		{
			component.setToolTipText(tip);
		}
		for (java.awt.Component child : component.getComponents())
		{
			if (child instanceof javax.swing.JComponent)
			{
				hover((javax.swing.JComponent) child, tip);
			}
		}
	}

	private static Color fractionColor(EventItem item)
	{
		if (item.approved > 0)
		{
			return PROGRESS_TEXT;
		}
		return SOURCE_TEXT;
	}

	private static JLabel statusLabel(EventTile tile)
	{
		String progress = AllowedDrops.progress(tile);
		String html = "<span style='color: " + statusColor(tile) + "'>" + escape(statusText(tile)) + "</span>";
		if (!progress.isEmpty())
		{
			html += " - " + escape(progress);
		}
		JLabel label = new JLabel("<html><body style='width: " + TEXT_WIDTH + "px'>" + html + "</body></html>");
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static String statusColor(EventTile tile)
	{
		if (tile.complete)
		{
			return "#7ec48c";
		}
		if (tile.partial)
		{
			return "#e0b04a";
		}
		return "#a0a0a0";
	}

	private static String tileTooltip(EventTile tile)
	{
		String progress = AllowedDrops.progress(tile);
		if (progress.isEmpty())
		{
			return tileTitle(tile) + " - " + statusText(tile);
		}
		return tileTitle(tile) + " - " + progress + " " + statusText(tile);
	}

	private static final class FitWidth extends JPanel
	{
		private FitWidth(java.awt.LayoutManager layout)
		{
			super(layout);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		@Override
		public Dimension getMaximumSize()
		{
			Dimension size = getPreferredSize();
			size.width = Integer.MAX_VALUE;
			return size;
		}
	}

	private String itemLabel(EventItem item, Integer itemId)
	{
		if (itemId != null)
		{
			String name = itemNames.get(itemId);
			if (name != null && !name.isEmpty())
			{
				return name;
			}
		}
		if (item.item == null || item.item.isEmpty())
		{
			return "Unknown item";
		}
		String spaced = item.item.replace('_', ' ');
		return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
	}

	private AsyncBufferedImage itemImage(Integer itemId)
	{
		if (itemId == null)
		{
			return null;
		}
		return itemImages.get(itemId);
	}

	private static Integer firstItemId(EventTile tile)
	{
		if (tile.items == null)
		{
			return null;
		}
		for (EventItem item : tile.items)
		{
			if (item == null)
			{
				continue;
			}
			Integer id = firstId(item.runeliteIds);
			if (id != null)
			{
				return id;
			}
		}
		return null;
	}

	private static Integer firstId(List<Integer> ids)
	{
		if (ids == null)
		{
			return null;
		}
		for (Integer id : ids)
		{
			if (id != null)
			{
				return id;
			}
		}
		return null;
	}

	private static String statusText(EventTile tile)
	{
		if (tile.complete)
		{
			return "Completed";
		}
		if (tile.partial)
		{
			return "Partially completed";
		}
		return "Not completed";
	}

	private static JLabel wrappingLabel(String text, boolean heading)
	{
		return wrappingLabel(text, heading, TEXT_WIDTH);
	}

	private static JLabel wrappingLabel(String text, boolean heading, int width)
	{
		String body = escape(text);
		if (heading)
		{
			body = "<b>" + body + "</b>";
		}
		JLabel label = new JLabel("<html><body style='width: " + width + "px'>" + body + "</body></html>");
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static String escape(String text)
	{
		if (text == null)
		{
			return "";
		}
		return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private void refreshButton()
	{
		fetchButton.setEnabled(!fetching && !coolingDown() && !currentKey().isEmpty());
	}
}
