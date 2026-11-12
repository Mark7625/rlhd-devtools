package rs117.hd.dev.editor;

import java.awt.Color;
import java.awt.Font;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

public final class EditorText {
	private EditorText() {}

	public static Builder builder(String text) { return new Builder(text); }

	public static final class Builder {
		private final String text;
		private Font font = WorkspaceStyle.UI;
		private Color color = WorkspaceStyle.TEXT;
		private Icon icon;
		private Border border;
		private String tooltip;
		private int alignment = SwingConstants.LEADING;

		private Builder(String text) { this.text = text; }
		public Builder font(Font font) { this.font = font; return this; }
		public Builder color(Color color) { this.color = color; return this; }
		public Builder icon(Icon icon) { this.icon = icon; return this; }
		public Builder border(Border border) { this.border = border; return this; }
		public Builder tooltip(String tooltip) { this.tooltip = tooltip; return this; }
		public Builder alignment(int alignment) { this.alignment = alignment; return this; }

		public JLabel build() {
			var label = new JLabel(text, icon, alignment);
			label.setFont(font);
			label.setForeground(color);
			label.setBorder(border);
			label.setToolTipText(tooltip);
			return label;
		}
	}
}
