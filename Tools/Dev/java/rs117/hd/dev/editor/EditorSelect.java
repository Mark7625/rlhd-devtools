package rs117.hd.dev.editor;

import java.awt.Dimension;
import javax.swing.*;

/** Shared dropdown chrome. The caller supplies the model and row renderer. */
public final class EditorSelect {
	private EditorSelect() {}

	public static <T> Builder<T> builder() { return new Builder<>(); }

	public static final class Builder<T> {
		private ComboBoxModel<T> model = new DefaultComboBoxModel<>();
		private ListCellRenderer<? super T> renderer;
		private java.util.function.Consumer<? super T> onChange;
		private Dimension size;
		private boolean enabled = true;
		private String tooltip;
		private T selection;
		private boolean hasSelection;

		private Builder() {}
		public Builder<T> model(ComboBoxModel<T> model) { this.model = model; return this; }

		public Builder<T> options(java.util.Collection<? extends T> options) {
			var values = new DefaultComboBoxModel<T>();
			options.forEach(values::addElement);
			return model(values);
		}

		public Builder<T> renderer(ListCellRenderer<? super T> renderer) { this.renderer = renderer; return this; }
		public Builder<T> selected(T value) { selection = value; hasSelection = true; return this; }
		public Builder<T> enabled(boolean enabled) { this.enabled = enabled; return this; }
		public Builder<T> size(int width, int height) { size = new Dimension(width, height); return this; }
		public Builder<T> tooltip(String tooltip) { this.tooltip = tooltip; return this; }
		public Builder<T> onChange(java.util.function.Consumer<? super T> action) { onChange = action; return this; }

		public JComboBox<T> build() {
			var selector = new JComboBox<>(model);
			style(selector);
			if (renderer != null)
				selector.setRenderer(renderer);
			if (hasSelection)
				selector.setSelectedItem(selection);
			if (size != null)
				selector.setPreferredSize(new Dimension(size));
			selector.setToolTipText(tooltip);
			selector.setEnabled(enabled);
			if (onChange != null)
				selector.addItemListener(event -> {
					if (event.getStateChange() == java.awt.event.ItemEvent.SELECTED)
						onChange.accept(selector.getItemAt(selector.getSelectedIndex()));
				});
			return selector;
		}
	}

	public static void style(JComboBox<?> selector) {
		selector.setUI(new javax.swing.plaf.basic.BasicComboBoxUI() {
			@Override protected JButton createArrowButton() {
				var button = new JButton(WorkspaceStyle.icon("chevron-down", 10));
				button.setOpaque(false);
				button.setContentAreaFilled(false);
				button.setBorderPainted(false);
				button.setFocusable(false);
				button.setPreferredSize(new Dimension(24, 24));
				return button;
			}

			@Override public void paintCurrentValueBackground(java.awt.Graphics graphics, java.awt.Rectangle bounds, boolean focus) {
				// The field paints its background across the value and arrow.
			}

			@Override public void paint(java.awt.Graphics graphics, JComponent component) {
				var g = (java.awt.Graphics2D) graphics.create();
				g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				int width = component.getWidth() - 1;
				int height = component.getHeight() - 1;
				g.setColor(WorkspaceStyle.INPUT);
				g.fillRoundRect(0, 0, width, height, 8, 8);
				g.setColor(selector.isPopupVisible() ? WorkspaceStyle.ACCENT : WorkspaceStyle.BORDER);
				g.drawRoundRect(0, 0, width, height, 8, 8);
				g.dispose();
				super.paint(graphics, component);
			}
		});
		selector.setOpaque(false);
		selector.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1));
		selector.setMinimumSize(new Dimension(0, 24));
		selector.setPreferredSize(new Dimension(150, 24));
	}
}
