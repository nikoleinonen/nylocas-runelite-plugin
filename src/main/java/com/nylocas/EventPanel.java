package com.nylocas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
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
import javax.swing.JToolTip;
import javax.swing.ScrollPaneConstants;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
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
	private static final int COOLDOWN_MS = 60_000;
	private static final int REFRESH_ICON = 16;
	private static final int LINK_ICON = 16;
	private static final String ICON_STYLE = "border: 3,0,3,0,#0000";
	private static final String DISCORD_URL = "https://discord.gg/3T23DAehU5";
	private static final String GITHUB_URL = "https://github.com/nikoleinonen/nylocas-runelite-plugin";
	private static final String WEBSITE_URL = "https://nylocas.com/";
	private static final String KEY_HINT = "Set team key in config";

	private final NylocasPlugin plugin;
	private final JButton refreshButton = new JButton()
	{
		@Override
		public String getToolTipText()
		{
			return refreshTooltip();
		}

		@Override
		public JToolTip createToolTip()
		{
			JToolTip tip = super.createToolTip();
			watchRefreshTip(tip);
			return tip;
		}
	};
	// A disabled button does not receive mouse events, so the cooldown tooltip is also on this host.
	private final JPanel refreshHost = new JPanel(new BorderLayout())
	{
		@Override
		public String getToolTipText()
		{
			return refreshTooltip();
		}

		@Override
		public JToolTip createToolTip()
		{
			JToolTip tip = super.createToolTip();
			watchRefreshTip(tip);
			return tip;
		}
	};
	private JToolTip refreshTip;
	private Timer refreshTipTimer;
	private int savedDismissDelay = -1;
	private final JLabel status = new ShrinkLabel(" ");
	private final JLabel rank = new JLabel();
	private final JPanel statusRow = new ShrinkWidth(new BorderLayout(8, 0));
	private final JPanel gridHost = new JPanel();
	private final JPanel detail = new JPanel();
	private final Map<JPanel, EventTile> cells = new HashMap<>();
	private Map<Integer, String> itemNames = Map.of();
	private Map<Integer, AsyncBufferedImage> itemImages = Map.of();
	private JPanel selectedCell;
	private boolean fetching;
	private boolean fetched;
	private volatile boolean stopped;
	private long cooldownUntil;
	private Timer cooldownTimer;

	EventPanel(NylocasPlugin plugin)
	{
		super();
		this.plugin = plugin;
		setBorder(new EmptyBorder(0, 0, PluginPanel.BORDER_OFFSET, 0));
		getScrollPane().setBorder(BorderFactory.createMatteBorder(1, 1, 1, 0, HEADER_LINE));
		// The bar keeps its column even when the list fits, so the board and item cards do not shift.
		getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);

		String accent = hex(TITLE_ACCENT);
		JLabel title = new JLabel("<html><b>Nylocas <span style='color: " + accent + "'>Clan Events</span></b></html>");
		title.setForeground(ColorScheme.TEXT_COLOR);

		BufferedImage icon = ImageUtil.resizeImage(ImageUtil.loadImageResource(getClass(), "refresh.png"), REFRESH_ICON, REFRESH_ICON);
		refreshButton.setIcon(new ImageIcon(icon));
		refreshButton.setToolTipText("Fetch event");
		refreshButton.setFocusable(false);
		refreshButton.setOpaque(false);
		refreshButton.setContentAreaFilled(false);
		SwingUtil.removeButtonDecorations(refreshButton);
		refreshButton.putClientProperty("FlatLaf.style", ICON_STYLE);
		refreshButton.addActionListener(event -> fetch());
		refreshHost.setOpaque(false);
		refreshHost.setToolTipText("Fetch event");
		refreshHost.add(refreshButton);

		JPanel titleWrap = new JPanel(new BorderLayout());
		titleWrap.setOpaque(false);
		titleWrap.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 0, 1, HEADER_LINE),
			new EmptyBorder(4, PluginPanel.BORDER_OFFSET, 4, 8)
		));
		titleWrap.add(title, BorderLayout.CENTER);

		JPanel buttons = new ShrinkWidth(new GridLayout(1, 4));
		buttons.setOpaque(false);
		buttons.setBorder(new EmptyBorder(4, 0, 4, 0));
		buttons.add(refreshHost);
		buttons.add(linkButton("website.png", "Website", WEBSITE_URL));
		buttons.add(linkButton("discord.png", "Discord", DISCORD_URL));
		buttons.add(linkButton("github.png", "GitHub", GITHUB_URL));

		JPanel titleRow = new JPanel(new BorderLayout());
		titleRow.setBackground(HEADER_COLOR);
		titleRow.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, HEADER_LINE));
		titleRow.add(titleWrap, BorderLayout.WEST);
		titleRow.add(buttons, BorderLayout.CENTER);
		add(titleRow);

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

		onTeamKeyChanged();
	}

	@Override
	public void onActivate()
	{
		if (stopped || fetched)
		{
			return;
		}
		fetched = fetch();
	}

	void onTeamKeyChanged()
	{
		if (stopped)
		{
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
		refreshButton();
	}

	private JButton linkButton(String resource, String tooltip, String url)
	{
		BufferedImage image = ImageUtil.resizeImage(ImageUtil.loadImageResource(getClass(), resource), LINK_ICON, LINK_ICON);
		JButton button = new JButton(new ImageIcon(image));
		button.setToolTipText(tooltip);
		button.setFocusable(false);
		button.setOpaque(false);
		button.setContentAreaFilled(false);
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

	private boolean fetch()
	{
		String key = currentKey();
		if (key.isEmpty() || fetching || coolingDown())
		{
			return false;
		}
		fetching = true;
		status.setText("Fetching...");
		clearRank();
		clearBoard();
		if (!plugin.fetchEvent(key, this))
		{
			return false;
		}
		startCooldown();
		return true;
	}

	void stop()
	{
		stopped = true;
		if (cooldownTimer != null)
		{
			cooldownTimer.stop();
		}
		clearRefreshTip();
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
			refreshButton();
		});
		cooldownTimer.setRepeats(false);
		cooldownTimer.start();
		refreshButton();
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
		status.setText(message);
		clearRank();
		clearBoard();
		refreshButton();
	}

	void showBoard(EventBoard board, Map<Integer, String> names, Map<Integer, AsyncBufferedImage> images)
	{
		if (stopped)
		{
			return;
		}
		fetching = false;
		status.setText(teamLabel(board));
		showRank(board);
		itemNames = names == null ? Map.of() : names;
		itemImages = images == null ? Map.of() : images;
		selectedCell = null;
		cells.clear();
		gridHost.removeAll();
		detail.removeAll();
		int rows = board == null ? 0 : board.rows;
		int cols = board == null ? 0 : board.cols;
		if (rows <= 0 || cols <= 0)
		{
			detail.add(wrappingLabel("No board on this event", false));
		}
		else
		{
			SquareGrid grid = new SquareGrid(rows, cols);
			grid.fit(boardWidth());
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
			detail.add(wrappingLabel("Select a tile", false));
		}
		gridHost.revalidate();
		gridHost.repaint();
		detail.revalidate();
		detail.repaint();
		revalidate();
		repaint();
		refreshButton();
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
		if (gridHost.getComponentCount() != 1 || !(gridHost.getComponent(0) instanceof SquareGrid))
		{
			return;
		}
		int width = gridHost.getWidth() - gridHost.getInsets().left - gridHost.getInsets().right;
		if (width <= 0)
		{
			return;
		}
		SquareGrid grid = (SquareGrid) gridHost.getComponent(0);
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
		cell.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent event)
			{
				select(cell);
			}
		});
		return cell;
	}

	private JPanel lockedCell(boolean empty)
	{
		JPanel cell = new JPanel(new GridLayout(1, 1));
		cell.setBackground(INCOMPLETE_COLOR);
		cell.setBorder(INCOMPLETE_BORDER);
		cell.setToolTipText(empty ? "Empty" : "Locked");
		return cell;
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
		detail.add(statusLabel(tile));
		String requirement = TileText.requirement(tile);
		if (!requirement.isEmpty())
		{
			detail.add(Box.createVerticalStrut(6));
			detail.add(wrappingLabel(requirement, false));
		}
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
		itemNames = Map.of();
		itemImages = Map.of();
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

	private static JLabel statusLabel(EventTile tile)
	{
		String progress = TileText.progress(tile);
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

	private static final class SquareGrid extends JPanel
	{
		private final int rows;
		private final int cols;
		private int cell = 1;
		private int gap = GRID_GAP;
		private int extra;

		private SquareGrid(int rows, int cols)
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
			cell = nextCell;
			gap = nextGap;
			extra = leftover;
			int gridWidth = nextCell * cols + nextGap * gaps + leftover;
			int gridHeight = nextCell * rows + nextGap * Math.max(0, rows - 1);
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
						getComponent(index).setBounds(x, y, cell, cell);
					}
					x += cell;
					if (col < cols - 1)
					{
						x += gap + (col < extra ? 1 : 0);
					}
				}
				y += cell;
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

	private String refreshTooltip()
	{
		if (!coolingDown())
		{
			return "Fetch event";
		}
		long seconds = (cooldownUntil - System.currentTimeMillis() + 999) / 1000;
		if (seconds < 1)
		{
			seconds = 1;
		}
		long minutes = seconds / 60;
		long remainder = seconds % 60;
		return "On cooldown (" + minutes + ":" + (remainder < 10 ? "0" : "") + remainder + ")";
	}

	private void watchRefreshTip(JToolTip tip)
	{
		if (refreshTipTimer != null)
		{
			refreshTipTimer.stop();
		}
		refreshTip = tip;
		tip.addHierarchyListener(event ->
		{
			if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && !tip.isShowing() && refreshTip == tip)
			{
				clearRefreshTip();
			}
		});
		if (savedDismissDelay < 0 && coolingDown())
		{
			ToolTipManager manager = ToolTipManager.sharedInstance();
			savedDismissDelay = manager.getDismissDelay();
			manager.setDismissDelay(COOLDOWN_MS);
		}
		refreshTipTimer = new Timer(1000, event ->
		{
			if (refreshTip == null || !refreshTip.isShowing())
			{
				clearRefreshTip();
				return;
			}
			refreshTip.setTipText(refreshTooltip());
		});
		refreshTipTimer.setRepeats(true);
		refreshTipTimer.start();
	}

	private void clearRefreshTip()
	{
		if (refreshTipTimer != null)
		{
			refreshTipTimer.stop();
			refreshTipTimer = null;
		}
		refreshTip = null;
		if (savedDismissDelay >= 0)
		{
			ToolTipManager.sharedInstance().setDismissDelay(savedDismissDelay);
			savedDismissDelay = -1;
		}
	}

	private void refreshButton()
	{
		refreshButton.setEnabled(!fetching && !coolingDown() && !currentKey().isEmpty());
		if (refreshTip != null && refreshTip.isShowing())
		{
			refreshTip.setTipText(refreshTooltip());
		}
	}
}
