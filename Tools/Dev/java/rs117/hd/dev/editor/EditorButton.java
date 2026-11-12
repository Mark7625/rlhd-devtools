package rs117.hd.dev.editor;

import java.awt.Dimension;
import java.util.Objects;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JToggleButton;

/** Builds ordinary Swing buttons with the editor's shared appearance. */
public final class EditorButton {
	private EditorButton() {}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private WorkspaceStyle.Button style = WorkspaceStyle.Button.SECONDARY;
		private String text;
		private Icon icon;
		private String tooltip;
		private boolean enabled = true;
		private Dimension size;
		private Runnable action;

		private Builder() {}

		public Builder style(WorkspaceStyle.Button style) {
			this.style = Objects.requireNonNull(style);
			return this;
		}

		public Builder text(String text) {
			this.text = text;
			return this;
		}

		public Builder icon(Icon icon) {
			this.icon = icon;
			return this;
		}

		public Builder icon(String name, int size) {
			return icon(WorkspaceStyle.icon(name, size));
		}

		public Builder tooltip(String tooltip) {
			this.tooltip = tooltip;
			return this;
		}

		public Builder enabled(boolean enabled) {
			this.enabled = enabled;
			return this;
		}

		public Builder size(int width, int height) {
			this.size = new Dimension(width, height);
			return this;
		}

		public Builder onClick(Runnable action) {
			this.action = Objects.requireNonNull(action);
			return this;
		}

		public JButton build() {
			return configure(new JButton());
		}

		public JToggleButton buildToggle(boolean selected) {
			var button = configure(new JToggleButton());
			button.setSelected(selected);
			return button;
		}

		private <T extends AbstractButton> T configure(T button) {
			WorkspaceStyle.button(button, style);
			button.setText(text);
			button.setIcon(icon);
			button.setEnabled(enabled);
			button.setToolTipText(tooltip);
			if ((text == null || text.isEmpty()) && tooltip != null)
				button.getAccessibleContext().setAccessibleName(tooltip);
			if (size != null)
				button.setPreferredSize(new Dimension(size));
			if (action != null)
				button.addActionListener(event -> action.run());
			return button;
		}
	}
}
