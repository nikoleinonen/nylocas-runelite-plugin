package com.nylocas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.SwingUtil;

final class EventPanel extends PluginPanel
{
	private static final int TEXT_WIDTH = 190;
	private static final int GRID_GAP = 2;
	private static final Color INCOMPLETE_COLOR = new Color(0x1e1e1e);
	private static final Color BORDER_LINE = new Color(0x171717);
	private static final Border CELL_BORDER = BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR, 1);
	private static final Border INCOMPLETE_BORDER = BorderFactory.createLineBorder(BORDER_LINE, 1);
	private static final Border SELECTED_BORDER = BorderFactory.createLineBorder(ColorScheme.TEXT_COLOR, 1);
	private static final Border ITEM_BORDER = BorderFactory.createCompoundBorder(
		BorderFactory.createLineBorder(BORDER_LINE, 1),
		new EmptyBorder(4, 4, 4, 6)
	);
	private static final Color COMPLETE_COLOR = new Color(0x10b981);
	private static final Color PARTIAL_COLOR = new Color(0xfbbf24);
	private static final Color MUTED_TEXT = new Color(0x7b8087);
	private static final Color PENDING_TEXT = new Color(0xfbbf24);
	private static final Color PROGRESS_TEXT = new Color(0x10b981);
	private static final Color TITLE_ACCENT = new Color(0x699172);
	private static final Color GOLD = new Color(0xe4c56a);
	private static final Color SILVER = new Color(0xc5c9d0);
	private static final Color BRONZE = new Color(0xc4894a);
	private static final Color INCOMPLETE_TEXT = new Color(0xa0a0a0);
	private static final Color HEADER_COLOR = new Color(0x1e1e1e);
	private static final Color HEADER_LINE = new Color(0x171717);
	private static final int COOLDOWN_MS = 2_000; // PUT TO 60_000 WHEN READY FOR PLUGIN HUB!
	private static final int LINK_ICON = 16;
	private static final String ICON_STYLE = "border: 3,0,3,0,#0000";
	private static final String DISCORD_URL = "https://discord.gg/3T23DAehU5";
	private static final String GITHUB_URL = "https://github.com/nikoleinonen/nylocas-runelite-plugin";
	private static final String WEBSITE_URL = "https://nylocas.com/";
	private static final String KEY_HINT = "Set team key in config";

	private final NylocasPlugin plugin;
	private final JButton sessionButton = new JButton("Connect");
	private final JLabel status = new ShrinkLabel(" ");
	private final JLabel rank = new JLabel();
	private final JPanel statusRow = new ShrinkWidth(new BorderLayout(8, 0));
	private final JPanel gridHost = new JPanel();
	private final JPanel detail = new JPanel();
	private final Map<JPanel, EventTile> cells = new HashMap<>();
	private final List<EventTile> knownTiles = new ArrayList<>();
	private int boardRows;
	private int boardCols;
	private int openRow;
	private int rowTicket;
	private boolean refreshingRow;
	private Map<Integer, String> itemNames = Map.of();
	private Map<Integer, AsyncBufferedImage> itemImages = Map.of();
	private Map<Long, TileIcons.Picture> pictures = Map.of();
	private JPanel selectedCell;
	private BufferedImage lockedIcon;
	private boolean fetching;
	private boolean refreshingBoard;
	private boolean connected;
	private volatile boolean acceptBoard;
	private volatile boolean stopped;
	private long cooldownUntil;
	private Timer cooldownTimer;

	EventPanel(NylocasPlugin plugin)
	{
		super();
		this.plugin = plugin;
		setBorder(new EmptyBorder(0, 0, PluginPanel.BORDER_OFFSET, 0));
		// The header and the connect row draw the top and bottom edges. This pane only keeps the left edge.
		getScrollPane().setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, HEADER_LINE));
		// The bar keeps its column even when the list fits, so the board and item cards do not shift.
		getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

		String accent = hex(TITLE_ACCENT);
		JLabel title = new JLabel("<html><b>Nylocas <span style='color: " + accent + "'>Clan Events</span></b></html>");
		title.setForeground(ColorScheme.TEXT_COLOR);

		JPanel titleWrap = new JPanel(new BorderLayout());
		titleWrap.setOpaque(false);
		titleWrap.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 0, 1, HEADER_LINE),
			new EmptyBorder(4, PluginPanel.BORDER_OFFSET, 4, 8)
		));
		titleWrap.add(title, BorderLayout.CENTER);

		JPanel buttons = new ShrinkWidth(new GridLayout(1, 3));
		buttons.setOpaque(false);
		buttons.setBorder(new EmptyBorder(4, 0, 4, 0));
		buttons.add(linkCell("website.png", "Website", WEBSITE_URL));
		buttons.add(linkCell("discord.png", "Discord", DISCORD_URL));
		buttons.add(linkCell("github.png", "GitHub", GITHUB_URL));

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setBackground(HEADER_COLOR);
		titleRow.setBorder(BorderFactory.createMatteBorder(1, 1, 1, 0, HEADER_LINE));
		titleRow.add(titleWrap, BorderLayout.WEST);
		titleRow.add(buttons, BorderLayout.CENTER);

		status.setForeground(ColorScheme.TEXT_COLOR);
		rank.setForeground(ColorScheme.TEXT_COLOR);
		rank.setHorizontalAlignment(JLabel.RIGHT);
		rank.setVisible(false);
		statusRow.setOpaque(false);
		statusRow.setBorder(new EmptyBorder(8, PluginPanel.BORDER_OFFSET, 0, PluginPanel.BORDER_OFFSET));
		statusRow.add(status, BorderLayout.CENTER);
		statusRow.add(rank, BorderLayout.EAST);
		add(statusRow);

		gridHost.setLayout(new BoxLayout(gridHost, BoxLayout.Y_AXIS));
		gridHost.setBackground(ColorScheme.DARK_GRAY_COLOR);
		gridHost.setBorder(new EmptyBorder(8, PluginPanel.BORDER_OFFSET, 0, PluginPanel.BORDER_OFFSET));
		gridHost.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				fitBoard();
			}
		});
		add(gridHost);

		detail.setLayout(new BoxLayout(detail, BoxLayout.Y_AXIS));
		detail.setBackground(ColorScheme.DARK_GRAY_COLOR);
		detail.setBorder(new EmptyBorder(8, PluginPanel.BORDER_OFFSET, 0, PluginPanel.BORDER_OFFSET));
		add(detail);

		sessionButton.setFocusable(false);
		sessionButton.setBackground(HEADER_COLOR);
		sessionButton.setForeground(ColorScheme.TEXT_COLOR);
		sessionButton.addActionListener(event -> session());
		JPanel sessionRow = new JPanel(new BorderLayout());
		sessionRow.setBackground(ColorScheme.DARK_GRAY_COLOR);
		sessionRow.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(1, 1, 1, 0, HEADER_LINE),
			new EmptyBorder(8, PluginPanel.BORDER_OFFSET, 8, PluginPanel.BORDER_OFFSET)
		));
		sessionRow.add(sessionButton, BorderLayout.CENTER);
		JPanel wrapped = getWrappedPanel();
		wrapped.setBackground(ColorScheme.DARK_GRAY_COLOR);
		wrapped.add(titleRow, BorderLayout.NORTH);
		wrapped.add(sessionRow, BorderLayout.SOUTH);

		onTeamKeyChanged();
	}

	void onTeamKeyChanged()
	{
		if (stopped)
		{
			return;
		}
		if (connected || fetching || !cells.isEmpty())
		{
			disconnect();
			return;
		}
		if (currentKey().isEmpty())
		{
			if (status.getText() == null || status.getText().isBlank())
			{
				status.setText(KEY_HINT);
			}
		}
		else if (KEY_HINT.equals(status.getText()))
		{
			status.setText(" ");
		}
		updateSessionButton();
	}

	void onWebsocketChanged()
	{
		if (stopped)
		{
			return;
		}
		updateSessionButton();
	}

	private JPanel linkCell(String resource, String tooltip, String url)
	{
		JPanel cell = new JPanel(new GridBagLayout());
		cell.setOpaque(false);
		cell.add(linkButton(resource, tooltip, url));
		return cell;
	}

	private JButton linkButton(String resource, String tooltip, String url)
	{
		BufferedImage image = ImageUtil.resizeImage(ImageUtil.loadImageResource(getClass(), resource), LINK_ICON, LINK_ICON);
		JButton button = new JButton(new ImageIcon(image));
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setOpaque(false);
		button.setContentAreaFilled(false);
		button.setHorizontalAlignment(JButton.CENTER);
		button.setVerticalAlignment(JButton.CENTER);
		button.setMargin(new Insets(0, 0, 0, 0));
		SwingUtil.removeButtonDecorations(button);
		button.putClientProperty("FlatLaf.style", ICON_STYLE);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.addActionListener(event -> LinkBrowser.browse(url));
		return button;
	}

	private String currentKey()
	{
		return plugin.teamKey();
	}

	private void session()
	{
		if (connected && plugin.websocketEnabled())
		{
			disconnect();
			return;
		}
		String key = currentKey();
		if (key.isEmpty() || fetching || coolingDown())
		{
			return;
		}
		boolean refresh = connected;
		fetching = true;
		refreshingBoard = refresh;
		acceptBoard = true;
		updateSessionButton();
		status.setText("Fetching...");
		if (!refresh)
		{
			clearRank();
			clearBoard();
		}
		if (!plugin.fetchEvent(key, this))
		{
			fetching = false;
			refreshingBoard = false;
			updateSessionButton();
			return;
		}
		startCooldown();
	}

	private void disconnect()
	{
		connected = false;
		fetching = false;
		refreshingBoard = false;
		acceptBoard = false;
		plugin.endSession();
		clearRank();
		clearBoard();
		status.setText(currentKey().isEmpty() ? KEY_HINT : " ");
		updateSessionButton();
	}

	void stop()
	{
		stopped = true;
		connected = false;
		refreshingBoard = false;
		acceptBoard = false;
		if (cooldownTimer != null)
		{
			cooldownTimer.stop();
		}
	}

	private void startCooldown()
	{
		cooldownUntil = System.currentTimeMillis() + COOLDOWN_MS;
		if (cooldownTimer != null)
		{
			cooldownTimer.stop();
		}
		cooldownTimer = new Timer(COOLDOWN_MS, event ->
		{
			cooldownUntil = 0;
			updateSessionButton();
		});
		cooldownTimer.setRepeats(false);
		cooldownTimer.start();
		updateSessionButton();
	}

	private boolean coolingDown()
	{
		return System.currentTimeMillis() < cooldownUntil;
	}

	void showStatus(String message)
	{
		if (stopped)
		{
			return;
		}
		fetching = false;
		if (refreshingBoard)
		{
			refreshingBoard = false;
			status.setText(message);
			updateSessionButton();
			return;
		}
		connected = false;
		acceptBoard = false;
		status.setText(message);
		clearRank();
		clearBoard();
		updateSessionButton();
	}

	void showBoard(EventBoard board, Map<Integer, String> names, Map<Integer, AsyncBufferedImage> images, Map<Long, TileIcons.Picture> tilePictures, boolean reopenSocket, int rowTicket)
	{
		if (stopped || !acceptBoard || (!reopenSocket && rowTicket != this.rowTicket))
		{
			return;
		}
		fetching = false;
		refreshingBoard = false;
		connected = true;
		refreshingRow = false;
		status.setText(teamLabel(board));
		showRank(board);
		itemNames = names == null ? Map.of() : names;
		itemImages = images == null ? Map.of() : images;
		pictures = tilePictures == null ? Map.of() : tilePictures;
		knownTiles.clear();
		if (board != null && board.tiles != null)
		{
			for (EventTile tile : board.tiles)
			{
				if (tile != null)
				{
					knownTiles.add(tile);
				}
			}
		}
		boardRows = board == null ? 0 : board.rows;
		boardCols = board == null ? 0 : board.cols;
		openRow = board == null ? 0 : board.openRow;
		selectedCell = null;
		paintGrid();
		updateSessionButton();
		if (reopenSocket)
		{
			plugin.openWebsocket(board);
			plugin.noteSession(board);
		}
	}

	void rowRefreshFailed()
	{
		refreshingRow = false;
	}

	void noteOpenRow(int next)
	{
		if (stopped || !connected || next == openRow)
		{
			return;
		}
		if (next > openRow)
		{
			rowTicket++;
			refreshingRow = false;
			openRow = next;
			long selectedId = selectedTileId();
			paintGrid();
			reselect(selectedId);
			return;
		}
		if (refreshingRow)
		{
			return;
		}
		refreshingRow = true;
		plugin.refreshEvent(next, rowTicket);
	}

	private void paintGrid()
	{
		selectedCell = null;
		cells.clear();
		gridHost.removeAll();
		detail.removeAll();
		if (boardRows <= 0 || boardCols <= 0)
		{
			detail.add(wrappingLabel("No board on this event", false));
		}
		else
		{
			BoardGrid grid = new BoardGrid(boardRows, boardCols);
			grid.fit(boardWidth());
			for (int row = 0; row < boardRows; row++)
			{
				for (int col = 0; col < boardCols; col++)
				{
					EventTile tile = tileAt(knownTiles, row, col);
					if (row < openRow || tile == null)
					{
						grid.add(lockedCell(tile == null && row >= openRow));
						continue;
					}
					grid.add(tileCell(tile));
				}
			}
			gridHost.add(grid);
			detail.add(wrappingLabel("Select a tile", false));
		}
		gridHost.revalidate();
		gridHost.repaint();
		detail.revalidate();
		detail.repaint();
		revalidate();
		repaint();
	}

	private long selectedTileId()
	{
		if (selectedCell == null)
		{
			return -1;
		}
		EventTile tile = cells.get(selectedCell);
		return tile == null ? -1 : tile.id;
	}

	private void reselect(long tileId)
	{
		if (tileId < 0)
		{
			return;
		}
		for (Map.Entry<JPanel, EventTile> entry : cells.entrySet())
		{
			EventTile tile = entry.getValue();
			if (tile != null && tile.id == tileId)
			{
				select(entry.getKey());
				return;
			}
		}
	}

	private int boardWidth()
	{
		int inner = gridHost.getWidth() - gridHost.getInsets().left - gridHost.getInsets().right;
		if (gridHost.getWidth() > 0)
		{
			return Math.max(1, inner);
		}
		int bar = getScrollPane().getVerticalScrollBar().getPreferredSize().width;
		if (bar <= 0)
		{
			bar = PluginPanel.SCROLLBAR_WIDTH;
		}
		Insets scroll = getScrollPane().getInsets();
		Insets panel = getInsets();
		Insets host = gridHost.getInsets();
		int view = PluginPanel.PANEL_WIDTH + PluginPanel.SCROLLBAR_WIDTH - scroll.left - scroll.right - bar;
		return Math.max(1, view - panel.left - panel.right - host.left - host.right);
	}

	private void fitBoard()
	{
		if (gridHost.getComponentCount() != 1 || !(gridHost.getComponent(0) instanceof BoardGrid))
		{
			return;
		}
		int width = gridHost.getWidth() - gridHost.getInsets().left - gridHost.getInsets().right;
		if (width <= 0)
		{
			return;
		}
		BoardGrid grid = (BoardGrid) gridHost.getComponent(0);
		if (grid.getPreferredSize().width == width)
		{
			return;
		}
		Dimension before = grid.getPreferredSize();
		grid.fit(width);
		if (grid.getPreferredSize().equals(before))
		{
			return;
		}
		gridHost.revalidate();
	}

	private JPanel tileCell(EventTile tile)
	{
		JPanel cell = new JPanel(new GridLayout(1, 1));
		cell.setBackground(tileColor(tile));
		cell.setBorder(tileBorder(tile));
		cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		String tip = tileTooltip(tile);
		cell.setToolTipText(tip);
		TileFace face = new TileFace();
		face.show(pictures.get(tile.id));
		face.setToolTipText(tip);
		cell.add(face);
		cells.put(cell, tile);
		MouseAdapter select = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				select(cell);
			}
		};
		cell.addMouseListener(select);
		face.addMouseListener(select);
		return cell;
	}

	private JPanel lockedCell(boolean empty)
	{
		JPanel cell = new JPanel(new GridBagLayout());
		cell.setBackground(INCOMPLETE_COLOR);
		cell.setBorder(INCOMPLETE_BORDER);
		String tip = empty ? "Empty" : "Locked";
		cell.setToolTipText(tip);
		if (!empty)
		{
			JLabel icon = new JLabel(new ImageIcon(lockedIcon()));
			icon.setToolTipText(tip);
			cell.add(icon);
		}
		return cell;
	}

	private BufferedImage lockedIcon()
	{
		if (lockedIcon == null)
		{
			lockedIcon = ImageUtil.loadImageResource(getClass(), "locked.png");
		}
		return lockedIcon;
	}

	private void select(JPanel cell)
	{
		if (selectedCell != null)
		{
			selectedCell.setBorder(tileBorder(cells.get(selectedCell)));
		}
		selectedCell = cell;
		cell.setBorder(SELECTED_BORDER);
		EventTile tile = cells.get(cell);
		detail.removeAll();
		if (tile == null)
		{
			detail.revalidate();
			detail.repaint();
			return;
		}
		detail.add(wrappingLabel(TileText.tileTitle(tile), true));
		detail.add(statusLine(tile));
		if (tile.items != null)
		{
			for (EventItem item : tile.items)
			{
				if (item == null)
				{
					continue;
				}
				detail.add(Box.createVerticalStrut(4));
				detail.add(itemCard(tile, item));
			}
		}
		detail.revalidate();
		detail.repaint();
		revalidate();
		repaint();
	}

	private void clearBoard()
	{
		selectedCell = null;
		cells.clear();
		knownTiles.clear();
		boardRows = 0;
		boardCols = 0;
		openRow = 0;
		refreshingRow = false;
		itemNames = Map.of();
		itemImages = Map.of();
		pictures = Map.of();
		gridHost.removeAll();
		detail.removeAll();
		gridHost.revalidate();
		gridHost.repaint();
		detail.revalidate();
		detail.repaint();
	}

	private static String teamLabel(EventBoard board)
	{
		if (board == null || board.teamName == null || board.teamName.isEmpty())
		{
			return " ";
		}
		return "<html><nobr><b>" + escape(board.teamName) + "</b></nobr></html>";
	}

	private void showRank(EventBoard board)
	{
		String place = board == null || board.rank == null ? "" : board.rank.trim();
		if (place.isEmpty())
		{
			clearRank();
			return;
		}
		Color color = rankColor(place);
		String hex = String.format("#%06X", color.getRGB() & 0xFFFFFF);
		rank.setForeground(color);
		rank.setText("<html><nobr><b><span style='color: " + hex + "'>" + escape(place) + "</span></b></nobr></html>");
		rank.setVisible(true);
	}

	private void clearRank()
	{
		rank.setText("");
		rank.setVisible(false);
	}

	static Color rankColor(String place)
	{
		if ("WINNER".equals(place) || "1st".equals(place))
		{
			return GOLD;
		}
		if ("2nd".equals(place))
		{
			return SILVER;
		}
		if ("3rd".equals(place))
		{
			return BRONZE;
		}
		return ColorScheme.TEXT_COLOR;
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
		return INCOMPLETE_COLOR;
	}

	private static Border tileBorder(EventTile tile)
	{
		if (tile != null && (tile.complete || tile.partial))
		{
			return CELL_BORDER;
		}
		return INCOMPLETE_BORDER;
	}

	private JPanel itemCard(EventTile tile, EventItem item)
	{
		String tip = sourceTip(item);
		JPanel card = new FitWidth(new BorderLayout(6, 0));
		card.setBackground(INCOMPLETE_COLOR);
		card.setBorder(ITEM_BORDER);
		card.setAlignmentX(LEFT_ALIGNMENT);
		hover(card, tip);

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
		hover(icon, tip);
		card.add(icon, BorderLayout.WEST);

		JLabel name = new JLabel(TileText.itemName(item, id -> itemNames.get(id)));
		name.setVerticalAlignment(JLabel.CENTER);
		name.setForeground(ColorScheme.TEXT_COLOR);
		hover(name, tip);
		card.add(name, BorderLayout.CENTER);

		JPanel count = countColumn(tile, item);
		hover(count, tip);
		card.add(count, BorderLayout.EAST);
		return card;
	}

	private JPanel countColumn(EventTile tile, EventItem item)
	{
		JLabel count = new JLabel(TileText.itemFraction(tile, item));
		count.setForeground(fractionColor(item));
		count.setHorizontalAlignment(JLabel.RIGHT);
		String pending = TileText.pendingText(item);
		if (pending.isEmpty())
		{
			count.setBorder(new EmptyBorder(0, 4, 0, 0));
			JPanel holder = new JPanel(new BorderLayout());
			holder.setOpaque(false);
			holder.add(count, BorderLayout.CENTER);
			return holder;
		}
		JLabel pendingLabel = new JLabel(pending);
		pendingLabel.setForeground(PENDING_TEXT);
		pendingLabel.setHorizontalAlignment(JLabel.RIGHT);
		JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setOpaque(false);
		column.setBorder(new EmptyBorder(0, 4, 0, 0));
		count.setAlignmentX(RIGHT_ALIGNMENT);
		pendingLabel.setAlignmentX(RIGHT_ALIGNMENT);
		column.add(count);
		column.add(pendingLabel);
		return column;
	}

	private static String sourceTip(EventItem item)
	{
		String sources = TileText.sourcesText(item);
		if (sources.isEmpty())
		{
			return "";
		}
		return "Allowed from " + sources;
	}

	private static void hover(JComponent component, String tip)
	{
		if (tip != null && !tip.isEmpty())
		{
			component.setToolTipText(tip);
		}
		for (Component child : component.getComponents())
		{
			if (child instanceof JComponent)
			{
				hover((JComponent) child, tip);
			}
		}
	}

	private static Color fractionColor(EventItem item)
	{
		if (item.approved > 0)
		{
			return PROGRESS_TEXT;
		}
		return MUTED_TEXT;
	}

	private static JPanel statusLine(EventTile tile)
	{
		JPanel row = new FitWidth(new BorderLayout(8, 0));
		row.setOpaque(false);
		row.add(statusLabel(tile), BorderLayout.CENTER);
		String requirement = TileText.requirement(tile);
		if (!requirement.isEmpty())
		{
			JLabel label = new JLabel(requirement);
			label.setHorizontalAlignment(JLabel.RIGHT);
			row.add(label, BorderLayout.EAST);
		}
		return row;
	}

	private static JLabel statusLabel(EventTile tile)
	{
		String progress = TileText.progress(tile);
		String html = "<span style='color: " + statusColor(tile) + "'>" + escape(statusText(tile)) + "</span>";
		if (!progress.isEmpty())
		{
			html += " - " + escape(progress);
		}
		JLabel label = new ShrinkLabel("<html><nobr>" + html + "</nobr></html>");
		label.setVerticalAlignment(JLabel.CENTER);
		return label;
	}

	private static String statusColor(EventTile tile)
	{
		if (tile.complete)
		{
			return hex(COMPLETE_COLOR);
		}
		if (tile.partial)
		{
			return hex(PARTIAL_COLOR);
		}
		return hex(INCOMPLETE_TEXT);
	}

	private static String hex(Color color)
	{
		return String.format("#%06X", color.getRGB() & 0xFFFFFF);
	}

	private static String tileTooltip(EventTile tile)
	{
		String progress = TileText.progress(tile);
		if (progress.isEmpty())
		{
			return TileText.tileTitle(tile) + " - " + statusText(tile);
		}
		return TileText.tileTitle(tile) + " - " + progress + " " + statusText(tile);
	}

	static int cellHeight(int cellWidth)
	{
		return Math.max(1, cellWidth * 3 / 4);
	}

	private static final class BoardGrid extends JPanel
	{
		private final int rows;
		private final int cols;
		private int cellWidth = 1;
		private int cellHeight = 1;
		private int gap = GRID_GAP;
		private int extra;

		private BoardGrid(int rows, int cols)
		{
			this.rows = rows;
			this.cols = cols;
			setAlignmentX(LEFT_ALIGNMENT);
			setBackground(ColorScheme.DARK_GRAY_COLOR);
		}

		private void fit(int width)
		{
			int gaps = Math.max(0, cols - 1);
			int nextGap = GRID_GAP;
			int nextCell = cols <= 0 ? 1 : (width - nextGap * gaps) / cols;
			if (nextCell < 1)
			{
				nextCell = 1;
			}
			int used = nextCell * cols + nextGap * gaps;
			if (used > width && gaps > 0)
			{
				nextGap = Math.max(0, (width - nextCell * cols) / gaps);
				used = nextCell * cols + nextGap * gaps;
			}
			int leftover = Math.max(0, width - used);
			if (gaps > 0 && leftover > 0)
			{
				nextGap += leftover / gaps;
				leftover %= gaps;
			}
			cellWidth = nextCell;
			cellHeight = EventPanel.cellHeight(nextCell);
			gap = nextGap;
			extra = leftover;
			int gridWidth = nextCell * cols + nextGap * gaps + leftover;
			int gridHeight = cellHeight * rows + nextGap * Math.max(0, rows - 1);
			Dimension size = new Dimension(Math.max(gridWidth, 1), Math.max(gridHeight, 1));
			setPreferredSize(size);
			setMinimumSize(size);
			setMaximumSize(size);
		}

		@Override
		public void doLayout()
		{
			int count = getComponentCount();
			int y = 0;
			for (int row = 0; row < rows; row++)
			{
				int x = 0;
				for (int col = 0; col < cols; col++)
				{
					int index = row * cols + col;
					if (index < count)
					{
						getComponent(index).setBounds(x, y, cellWidth, cellHeight);
					}
					x += cellWidth;
					if (col < cols - 1)
					{
						x += gap + (col < extra ? 1 : 0);
					}
				}
				y += cellHeight;
				if (row < rows - 1)
				{
					y += gap;
				}
			}
		}
	}

	private static final class ShrinkWidth extends JPanel
	{
		private ShrinkWidth(LayoutManager layout)
		{
			super(layout);
		}

		@Override
		public Dimension getPreferredSize()
		{
			Dimension size = super.getPreferredSize();
			size.width = 0;
			return size;
		}

		@Override
		public Dimension getMinimumSize()
		{
			Dimension size = super.getMinimumSize();
			size.width = 0;
			return size;
		}
	}

	private static final class ShrinkLabel extends JLabel
	{
		private ShrinkLabel(String text)
		{
			super(text);
		}

		@Override
		public Dimension getPreferredSize()
		{
			Dimension size = super.getPreferredSize();
			size.width = 0;
			return size;
		}

		@Override
		public Dimension getMinimumSize()
		{
			Dimension size = super.getMinimumSize();
			size.width = 0;
			return size;
		}
	}

	private static final class FitWidth extends JPanel
	{
		private FitWidth(LayoutManager layout)
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

	private AsyncBufferedImage itemImage(Integer itemId)
	{
		if (itemId == null)
		{
			return null;
		}
		return itemImages.get(itemId);
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

	boolean clearBecauseEventEnded()
	{
		if (stopped || (!connected && cells.isEmpty() && !fetching))
		{
			return false;
		}
		disconnect();
		return true;
	}

	void socketFinished()
	{
		if (stopped || !connected)
		{
			return;
		}
		connected = false;
		updateSessionButton();
	}

	void showPlace(String place)
	{
		if (stopped || place == null || place.isBlank())
		{
			return;
		}
		EventBoard board = new EventBoard();
		board.rank = place;
		showRank(board);
	}

	String tileTitle(long tileId)
	{
		EventTile tile = tileById(tileId);
		return tile == null ? "" : TileText.tileTitle(tile);
	}

	String itemLabel(long tileId, String itemKey)
	{
		EventTile tile = tileById(tileId);
		if (tile != null && tile.items != null)
		{
			for (EventItem item : tile.items)
			{
				if (item != null && itemKey != null && itemKey.equals(item.key))
				{
					return TileText.itemName(item, itemNames::get);
				}
			}
		}
		return TileText.catalogName(itemKey);
	}

	void applyProgress(long tileId, String itemKey, int approved, int pending, boolean complete)
	{
		if (stopped)
		{
			return;
		}
		for (Map.Entry<JPanel, EventTile> entry : cells.entrySet())
		{
			EventTile tile = entry.getValue();
			if (tile == null || tile.id != tileId)
			{
				continue;
			}
			if (tile.items != null)
			{
				for (EventItem item : tile.items)
				{
					if (item != null && itemKey != null && itemKey.equals(item.key))
					{
						item.approved = approved;
						item.pending = pending;
					}
				}
			}
			tile.complete = complete;
			tile.partial = !complete && approvedCount(tile) > 0;
			JPanel cell = entry.getKey();
			String tip = tileTooltip(tile);
			cell.setBackground(tileColor(tile));
			cell.setToolTipText(tip);
			if (cell.getComponentCount() == 1 && cell.getComponent(0) instanceof TileFace)
			{
				((TileFace) cell.getComponent(0)).setToolTipText(tip);
			}
			if (cell == selectedCell)
			{
				cell.setBorder(tileBorder(tile));
				selectedCell = null;
				select(cell);
			}
			else
			{
				cell.setBorder(tileBorder(tile));
			}
			return;
		}
	}

	private static int approvedCount(EventTile tile)
	{
		if (tile == null || tile.items == null)
		{
			return 0;
		}
		int count = 0;
		for (EventItem item : tile.items)
		{
			if (item != null)
			{
				count += item.approved;
			}
		}
		return count;
	}

	private EventTile tileById(long tileId)
	{
		for (EventTile tile : cells.values())
		{
			if (tile != null && tile.id == tileId)
			{
				return tile;
			}
		}
		return null;
	}

	private void updateSessionButton()
	{
		boolean socket = plugin.websocketEnabled();
		if (socket)
		{
			sessionButton.setText(connected ? "Disconnect" : "Connect");
		}
		else
		{
			sessionButton.setText(connected ? "Refresh" : "Fetch event");
		}
		boolean busy = stopped || fetching;
		if (connected && socket)
		{
			sessionButton.setEnabled(!busy);
			return;
		}
		sessionButton.setEnabled(!busy && !currentKey().isEmpty() && !coolingDown());
	}
}
