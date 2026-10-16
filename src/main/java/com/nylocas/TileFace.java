package com.nylocas;

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import net.runelite.client.util.AsyncBufferedImage;

final class TileFace extends JComponent
{
	private Image image;
	private boolean cover;
	private boolean sharp;
	private Integer count;

	TileFace()
	{
		setOpaque(false);
	}

	void show(TileIcons.Picture picture)
	{
		if (picture == null)
		{
			image = null;
			count = null;
			return;
		}
		image = picture.image;
		cover = picture.cover;
		sharp = picture.sharp;
		count = picture.count;
		if (image instanceof AsyncBufferedImage)
		{
			((AsyncBufferedImage) image).onLoaded(this::repaint);
		}
	}

	@Override
	protected void paintComponent(Graphics graphics)
	{
		super.paintComponent(graphics);
		if (image == null)
		{
			return;
		}
		int imageWidth = image.getWidth(this);
		int imageHeight = image.getHeight(this);
		if (imageWidth <= 0 || imageHeight <= 0)
		{
			return;
		}
		int width = getWidth();
		int height = getHeight();
		if (width <= 0 || height <= 0)
		{
			return;
		}
		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setClip(0, 0, width, height);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, sharp
				? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
				: RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			if (cover)
			{
				paintCover(g, imageWidth, imageHeight, width, height);
			}
			else
			{
				paintIcon(g, imageWidth, imageHeight, width, height);
			}
			if (!cover && count != null)
			{
				paintCount(g, width, height);
			}
		}
		finally
		{
			g.dispose();
		}
	}

	private void paintCover(Graphics2D g, int imageWidth, int imageHeight, int width, int height)
	{
		// The cell background is the completion color, so the image stays inside a rim.
		int inset = 2;
		int innerWidth = Math.max(1, width - inset * 2);
		int innerHeight = Math.max(1, height - inset * 2);
		g.setClip(inset, inset, innerWidth, innerHeight);
		double scale = Math.max((double) innerWidth / imageWidth, (double) innerHeight / imageHeight);
		int drawnWidth = Math.max(1, (int) Math.round(imageWidth * scale));
		int drawnHeight = Math.max(1, (int) Math.round(imageHeight * scale));
		int x = inset + (innerWidth - drawnWidth) / 2;
		int y = inset + (innerHeight - drawnHeight) / 2;
		g.drawImage(image, x, y, drawnWidth, drawnHeight, this);
	}

	private void paintIcon(Graphics2D g, int imageWidth, int imageHeight, int width, int height)
	{
		int pad = 2;
		int boxWidth = Math.max(1, width - pad * 2);
		int boxHeight = Math.max(1, height - pad * 2);
		double scale = Math.min((double) boxWidth / imageWidth, (double) boxHeight / imageHeight);
		int drawnWidth = Math.max(1, (int) Math.round(imageWidth * scale));
		int drawnHeight = Math.max(1, (int) Math.round(imageHeight * scale));
		int x = (width - drawnWidth) / 2;
		int y = (height - drawnHeight) / 2;
		g.drawImage(image, x, y, drawnWidth, drawnHeight, this);
	}

	private void paintCount(Graphics2D g, int width, int height)
	{
		int size = Math.max(8, Math.min(11, height / 3));
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, size));
		String text = TileIcons.formatCount(count);
		int x = 2;
		int y = size;
		if (g.getFontMetrics().stringWidth(text) > width - 2)
		{
			return;
		}
		g.setColor(java.awt.Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(TileIcons.countColor(count));
		g.drawString(text, x, y);
	}
}
