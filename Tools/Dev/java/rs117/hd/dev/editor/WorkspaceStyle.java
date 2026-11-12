package rs117.hd.dev.editor;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import javax.swing.*;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

public final class WorkspaceStyle {
	public static final Color GAP = new Color(0x141414);
	public static final Color BAR = new Color(0x232323);
	public static final Color PANEL = new Color(0x262626);
	public static final Color HEADER = new Color(0x2C2C2C);
	public static final Color INPUT = new Color(0x1F1F1F);
	public static final Color MENU = new Color(0x1E1E1E);
	public static final Color BORDER = new Color(0x3A3A3A);
	public static final Color ACCENT = new Color(0x4772B3);
	public static final Color TEXT = new Color(0xCFCFCF);
	public static final Color DIM = new Color(0x8A8A8A);
	public static final Font UI = loadFont("ibmplexsans-400", Font.SANS_SERIF, 12);
	public static final Font SEMIBOLD = loadFont("ibmplexsans-600", Font.SANS_SERIF, 12);
	public static final Font MONO = loadFont("jetbrainsmono-400", Font.MONOSPACED, 11);

	private static final java.util.Map<String, Cursor> CURSORS = new java.util.HashMap<>();

	public static Cursor cursor(String name) {
		if (GraphicsEnvironment.isHeadless())
			return Cursor.getDefaultCursor();
		return CURSORS.computeIfAbsent(name, key -> {
			var resource = WorkspaceStyle.class.getResource("/rs117/hd/dev/editor/cursors/cursor-" + key + ".png");
			Toolkit toolkit = Toolkit.getDefaultToolkit();
			if (resource == null || toolkit.getBestCursorSize(32, 32).width == 0)
				return Cursor.getDefaultCursor();
			int hotspot = "box-select".equals(key) ? 6 : 16;
			return toolkit.createCustomCursor(new ImageIcon(resource).getImage(), new Point(hotspot, hotspot), key);
		});
	}

	private static final java.util.Map<String, ImageIcon> SVG_ICONS = new java.util.HashMap<>();

	public static ImageIcon icon(String name) {
		return icon(name, 16);
	}

	public static ImageIcon icon(String name, int size) {
		return SVG_ICONS.computeIfAbsent(name + "/" + size, key -> {
			var resource = WorkspaceStyle.class.getResource("/rs117/hd/dev/editor/icons/" + name + ".svg");
			if (resource == null)
				return new ImageIcon();
			var svg = new com.github.weisj.jsvg.parser.SVGLoader().load(resource);
			if (svg == null)
				return new ImageIcon();
			var image = new java.awt.image.BufferedImage(size, size, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = image.createGraphics();
			try {
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				svg.render(null, g, new com.github.weisj.jsvg.attributes.ViewBox(0, 0, size, size));
			} finally {
				g.dispose();
			}
			return new ImageIcon(image);
		});
	}

	public enum Button { TAB, PRIMARY, SECONDARY, DANGER, TOOL, SEGMENT, TRIGGER }

	private static Font loadFont(String file, String fallback, int size) {
		try (InputStream in = WorkspaceStyle.class.getResourceAsStream("/rs117/hd/dev/editor/fonts/" + file + ".ttf")) {
			if (in != null)
				return Font.createFont(Font.TRUETYPE_FONT, in).deriveFont((float) size);
		} catch (IOException | FontFormatException e) {
			throw new IllegalStateException("Unable to load editor font " + file, e);
		}
		return new Font(fallback, Font.PLAIN, size);
	}

	static boolean hasButtonStyle(AbstractButton button) {
		return button.getClientProperty(Button.class) != null;
	}

	public static void button(AbstractButton button, Button variant) {
		button.putClientProperty(Button.class, variant);
		int height = variant == Button.TOOL ? 32 : variant == Button.TRIGGER ? 28 : variant == Button.SEGMENT ? 22 : 24;
		button.setUI(new BasicButtonUI() {
			@Override
			public Dimension getPreferredSize(JComponent component) {
				Dimension size = super.getPreferredSize(component);
				return new Dimension(variant == Button.TOOL ? 32 : size.width, height);
			}
			@Override
			protected void paintText(Graphics g, AbstractButton button, Rectangle textRect, String text) {
				if (button.isEnabled()) {
					g.setColor(button.isSelected() ? Color.WHITE : button.getForeground());
				} else {
					g.setColor(new Color(0x666666));
				}
				g.drawString(text, textRect.x, textRect.y + g.getFontMetrics().getAscent());
			}

			@Override
			public void paint(Graphics graphics, JComponent component) {
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				ButtonModel model = button.getModel();
				Color background = null;
				switch (variant) {
					case PRIMARY: background = model.isRollover() ? new Color(0x5281C4) : ACCENT; break;
					case DANGER: background = model.isRollover() ? new Color(0x4A2C2C) : new Color(0x3A2626); break;
					case SECONDARY: background = model.isRollover() ? new Color(0x3D3D3D) : new Color(0x333333); break;
					case TAB: background = model.isSelected() || model.isRollover() ? BORDER : null; break;
					case TOOL: background = model.isSelected() ? ACCENT : model.isRollover() ? new Color(0x333333) : null; break;
					case SEGMENT: background = model.isSelected() ? new Color(0x454545) : model.isRollover() ? HEADER : null; break;
					case TRIGGER: background = model.isRollover() ? PANEL : MENU; break;
				}
				if (background != null) {
					g.setColor(background);
					int arc = variant == Button.TOOL || variant == Button.TRIGGER ? 10 : 8;
					g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), arc, arc);
				}
				super.paint(g, component);
				if (Boolean.TRUE.equals(button.getClientProperty("dropdown")))
					icon("chevron-down", 9).paintIcon(component, g, component.getWidth() - 17, (component.getHeight() - 9) / 2);
				g.dispose();
			}
		});
		button.setOpaque(false);
		button.setBorderPainted(false);
		button.setContentAreaFilled(false);
		button.setFocusPainted(false);
		button.setRolloverEnabled(true);
		button.setForeground(variant == Button.PRIMARY ? Color.WHITE : variant == Button.DANGER ? new Color(0xE8A0A0) : variant == Button.TAB ? new Color(0x9A9A9A) : TEXT);
		button.setFont(UI);
		button.setIconTextGap(6);
		button.setBorder(BorderFactory.createEmptyBorder(0, variant == Button.TRIGGER ? 8 : 10, 0, variant == Button.TRIGGER ? 8 : 10));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

	}

	public static void dropdown(AbstractButton button) {
		button.putClientProperty("dropdown", true);
		button.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 23));
	}

	public static void soon(AbstractButton button) {
		Icon badge = new Icon() {
			public int getIconWidth() { return 34; }
			public int getIconHeight() { return 14; }
			public void paintIcon(Component c, Graphics graphics, int x, int y) {
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(new Color(0x444444));
				g.drawRoundRect(x, y, 33, 13, 5, 5);
				g.setFont(UI.deriveFont(9f));
				g.setColor(new Color(0x7D7D7D));
				g.drawString("SOON", x + (34 - g.getFontMetrics().stringWidth("SOON")) / 2, y + 10);
				g.dispose();
			}
		};
		button.setHorizontalTextPosition(SwingConstants.LEFT);
		button.setIcon(badge);
		button.setDisabledIcon(badge);
	}

	public static void divider(JSplitPane split) {
		split.setUI(new BasicSplitPaneUI() {
			@Override
			public BasicSplitPaneDivider createDefaultDivider() {
				return new BasicSplitPaneDivider(this) {
					@Override
					public void paint(Graphics g) {
						g.setColor(isMouseOver() ? ACCENT : GAP);
						g.fillRect(0, 0, getWidth(), getHeight());
					}

					@Override
					public void setMouseOver(boolean over) {
						super.setMouseOver(over);
						repaint();
					}
				};
			}
		});
		split.setBorder(null);
		split.setDividerSize(4);
		split.setContinuousLayout(true);
	}

	public static JScrollPane listScroll(JList<?> list) {
		list.setBackground(PANEL);
		list.setForeground(TEXT);
		var scroll = new JScrollPane(list, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, MENU));
		scroll.getViewport().setBackground(PANEL);
		return scroll;
	}

	public static JTextField search(String placeholder) {
		return new JTextField() {
			@Override
			protected void paintComponent(Graphics graphics) {
				super.paintComponent(graphics);
				if (!getText().isEmpty())
					return;
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				g.setFont(getFont());
				g.setColor(DIM);
				FontMetrics metrics = g.getFontMetrics();
				g.drawString(placeholder, getInsets().left, (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent());
				g.dispose();
			}
		};
	}

	public static Icon badge(String text, Color color) {
		int size = text.isEmpty() ? 8 : 16;
		return new Icon() {
			public int getIconWidth() { return size; }
			public int getIconHeight() { return size; }

			public void paintIcon(Component c, Graphics graphics, int x, int y) {
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(color);
				g.fillRoundRect(x, y, size, size, 4, 4);
				g.setColor(GAP);
				g.setFont(MONO.deriveFont(Font.BOLD, 9f));
				FontMetrics metrics = g.getFontMetrics();
				g.drawString(text, x + (size - metrics.stringWidth(text)) / 2,
					y + (size - metrics.getHeight()) / 2 + metrics.getAscent());
				g.dispose();
			}
		};
	}

	private WorkspaceStyle() {}
}
