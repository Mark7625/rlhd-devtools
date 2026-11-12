package rs117.hd.dev.lights;

import rs117.hd.dev.editor.WorkspaceStyle;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;

/** Shared styling for the lights dev editor. */
public final class DevUi {
	public static final int LABEL_WIDTH = 78;
	public static final int FIELD_HEIGHT = 24;
	public static final float BODY_FONT_SIZE = 12f;
	public static final float SEGMENT_FONT_SIZE = 13f;

	private static final Border CARD_BORDER = BorderFactory.createCompoundBorder(
		BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
		BorderFactory.createEmptyBorder(8, 10, 8, 10));

	private DevUi() {}

	public static void themeFrame(JComponent root) {
		root.setBackground(ColorScheme.DARK_GRAY_COLOR);
		root.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
	}

	public static void themePanel(JPanel panel) {
		panel.setOpaque(true);
		panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
		panel.setForeground(new Color(0x9A9A9A));
	}

	public static JLabel titleLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
		label.setForeground(new Color(0x9A9A9A));
		return label;
	}

	public static JLabel subtitleLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
		label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		return label;
	}

	public static JLabel mutedLabel(String text) {
		JLabel label = new JLabel(text);
		label.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		return label;
	}

	public static JPanel card() {
		JPanel card = new JPanel(new BorderLayout(0, 4));
		themePanel(card);
		card.setBorder(CARD_BORDER);
		return card;
	}

	public static Font bodyFont(Font base) {
		return WorkspaceStyle.UI;
	}

	public static void styleMainTabs(javax.swing.JTabbedPane tabs) {
		tabs.setFont(bodyFont(tabs.getFont()));
	}

	public static void styleCombo(javax.swing.JComboBox<?> combo) {
		combo.setFont(bodyFont(combo.getFont()));
		sizeField(combo);
	}

	public static void styleList(javax.swing.JList<?> list) {
		list.setFont(bodyFont(list.getFont()));
	}

	public static void styleToolbarButton(JButton button) {
		compactButton(button);
		button.setFont(bodyFont(button.getFont()));
	}

	public static void applyListRendererFont(javax.swing.DefaultListCellRenderer renderer) {
		renderer.setFont(bodyFont(renderer.getFont()));
	}

	public static void compactButton(javax.swing.AbstractButton button) {
		button.setFocusPainted(false);
		button.setMargin(new java.awt.Insets(4, 8, 4, 8));
	}

	public static JPanel segmentBar(javax.swing.AbstractButton... buttons) {
		JPanel bar = new JPanel(new GridLayout(1, buttons.length, 0, 0));
		bar.setOpaque(false);
		bar.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(0, 0, 4, 0)));
		for (javax.swing.AbstractButton button : buttons) {
			compactButton(button);
			bar.add(button);
		}
		return bar;
	}

	public static void styleSegmentButton(javax.swing.AbstractButton button, boolean selected) {
		button.setBackground(selected ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		Font font = button.getFont().deriveFont(SEGMENT_FONT_SIZE);
		button.setFont(selected ? font.deriveFont(Font.BOLD) : font.deriveFont(Font.PLAIN));
	}

	public static void sizeField(JComponent field) {
		Dimension size = new Dimension(field.getPreferredSize().width, FIELD_HEIGHT);
		field.setPreferredSize(size);
		field.setMinimumSize(new Dimension(40, FIELD_HEIGHT));
	}

	public static JComponent formRow(String label, JComponent field) {
		sizeField(field);
		JPanel row = new JPanel(new net.miginfocom.swing.MigLayout("insets 0, gap 8 0, fillx", "[78!][grow,fill]", "[24!]"));
		row.setOpaque(false);
		row.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, FIELD_HEIGHT + 6));

		JLabel l = new JLabel(label);
		l.setFont(bodyFont(l.getFont()));
		l.setPreferredSize(new Dimension(LABEL_WIDTH, FIELD_HEIGHT));
		l.setForeground(new Color(0x9A9A9A));
		row.add(l);
		row.add(field);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		return row;
	}

	public static JComponent vectorRow(String label, JComponent x, JComponent y, JComponent z) {
		sizeField(x);
		sizeField(y);
		sizeField(z);
		JPanel trio = new JPanel(new GridLayout(1, 3, 6, 0));
		trio.setOpaque(false);
		trio.add(x);
		trio.add(y);
		trio.add(z);
		return formRow(label, trio);
	}

	public static Section section(String title, boolean expanded, Runnable onToggle, JComponent... rows) {
		var section = rs117.hd.dev.editor.EditorSection.builder(title)
			.expanded(expanded)
			.dividerColor(ColorScheme.MEDIUM_GRAY_COLOR);
		if (onToggle != null)
			section.onToggle(open -> onToggle.run());
		for (JComponent row : rows)
			section.add(row);
		var built = section.build();
		return new Section(built, built.content());
	}

	public static JScrollPane scroll(JComponent body) {
		JPanel wrap = new JPanel();
		wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));
		themePanel(wrap);
		wrap.setBorder(BorderFactory.createEmptyBorder(4, 2, 8, 2));
		wrap.add(body);
		wrap.add(Box.createVerticalGlue());

		JScrollPane scroll = new JScrollPane(wrap,
			ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
	}

	public static final class Section {
		public final JPanel root;
		public final JPanel content;

		Section(JPanel root, JPanel content) {
			this.root = root;
			this.content = content;
		}
	}
}
