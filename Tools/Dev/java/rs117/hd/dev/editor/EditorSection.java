package rs117.hd.dev.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.border.Border;

public final class EditorSection extends JPanel {
	private final JPanel body;
	private final JLabel chevron;
	private final Consumer<Boolean> onToggle;
	private final boolean collapsible;

	private EditorSection(Builder builder) {
		super();
		body = builder.body;
		onToggle = builder.onToggle;
		collapsible = builder.collapsible;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setOpaque(false);
		setAlignmentX(Component.LEFT_ALIGNMENT);
		var title = EditorText.builder(builder.title).font(WorkspaceStyle.SEMIBOLD).color(builder.titleColor).build();
		var header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(WorkspaceStyle.HEADER);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, builder.dividerColor),
			BorderFactory.createEmptyBorder(4, 10, 4, 10)));
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);
		header.add(title);
		chevron = new JLabel("", SwingConstants.CENTER);
		chevron.setPreferredSize(new Dimension(16, 20));
		if (collapsible) {
			header.add(chevron, BorderLayout.WEST);
			header.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			var toggle = new MouseAdapter() {
				@Override public void mouseClicked(MouseEvent event) {
					if (SwingUtilities.isLeftMouseButton(event))
						setExpanded(!body.isVisible());
				}
			};
			header.addMouseListener(toggle);
			title.addMouseListener(toggle);
			chevron.addMouseListener(toggle);
		}
		body.setBorder(builder.bodyBorder);
		add(header);
		add(body);
		updateExpanded(!collapsible || builder.expanded);
	}

	public JPanel content() { return body; }
	public boolean isExpanded() { return body.isVisible(); }

	public void setExpanded(boolean expanded) {
		if (!collapsible || isExpanded() == expanded)
			return;
		updateExpanded(expanded);
		if (onToggle != null)
			onToggle.accept(expanded);
	}

	private void updateExpanded(boolean expanded) {
		body.setVisible(expanded);
		chevron.setIcon(WorkspaceStyle.icon(expanded ? "chevron-down" : "chevron-right", 9));
		revalidate();
		repaint();
	}

	public static Builder builder(String title) { return new Builder(title); }

	public static final class Builder {
		private final String title;
		private JPanel body = new JPanel();
		private Border bodyBorder = BorderFactory.createEmptyBorder(2, 4, 6, 0);
		private Color titleColor = new Color(0x9A9A9A);
		private Color dividerColor = WorkspaceStyle.BORDER;
		private Consumer<Boolean> onToggle;
		private boolean collapsible = true;
		private boolean expanded = true;

		private Builder(String title) {
			this.title = title;
			body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
			body.setOpaque(false);
		}

		public Builder content(JPanel body) { this.body = body; return this; }
		public Builder add(JComponent child) { body.add(child); return this; }
		public Builder bodyBorder(Border border) { bodyBorder = border; return this; }
		public Builder titleColor(Color color) { titleColor = color; return this; }
		public Builder dividerColor(Color color) { dividerColor = color; return this; }
		public Builder collapsible(boolean value) { collapsible = value; return this; }
		public Builder expanded(boolean value) { expanded = value; return this; }
		public Builder onToggle(Consumer<Boolean> action) { onToggle = action; return this; }
		public EditorSection build() { return new EditorSection(this); }
	}
}
