package rs117.hd.dev.editor;

import java.awt.*;
import java.awt.event.ActionListener;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import javax.swing.plaf.basic.BasicMenuItemUI;

public final class WorkspaceMenu extends JPopupMenu {
	private final int width;

	public WorkspaceMenu(int width) {
		this.width = width;
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
	}

	@Override protected void paintComponent(Graphics graphics) {
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(WorkspaceStyle.MENU);
		g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
		g.setColor(WorkspaceStyle.BORDER);
		g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
		g.dispose();
	}

	public void addRow(String label, Icon icon, String shortcut, BooleanSupplier selected, ActionListener action) {
		addRow(label, icon, shortcut, selected, false, action);
	}

	public void addToggle(String label, boolean initial, java.util.function.Consumer<Boolean> action) {
		var selected = new javax.swing.JToggleButton.ToggleButtonModel();
		selected.setSelected(initial);
		addRow(label, null, "", selected::isSelected, true, e -> {
			selected.setSelected(!selected.isSelected());
			action.accept(selected.isSelected());
		});
	}

	private void addRow(String label, Icon icon, String shortcut, BooleanSupplier selected, boolean toggle, ActionListener action) {
		var item = new JMenuItem(label, icon);
		item.setUI(new BasicMenuItemUI() {
			@Override protected void doClick(MenuSelectionManager manager) {
				if (toggle)
					item.doClick(0);
				else
					super.doClick(manager);
			}

			@Override public void paint(Graphics graphics, JComponent component) {
				Graphics2D g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
				boolean active = selected.getAsBoolean();
				if (active && !toggle || item.isArmed()) {
					g.setColor(active && !toggle ? WorkspaceStyle.ACCENT : new Color(0x333333));
					g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 8, 8);
				}
				int x = 8;
				if (toggle) {
					g.setColor(active ? WorkspaceStyle.ACCENT : WorkspaceStyle.BORDER);
					g.fillRoundRect(x, 6, 14, 14, 6, 6);
					if (active)
						WorkspaceStyle.icon("check", 10).paintIcon(component, g, x + 2, 8);
					x += 22;
				} else if (icon != null) {
					icon.paintIcon(component, g, x, (component.getHeight() - icon.getIconHeight()) / 2);
					x += icon.getIconWidth() + 8;
				}
				g.setFont(WorkspaceStyle.UI);
				g.setColor(active ? Color.WHITE : WorkspaceStyle.TEXT);
				g.drawString(label, x, 17);
				g.setFont(WorkspaceStyle.MONO.deriveFont(10f));
				g.setColor(active ? new Color(0xCFE0FF) : WorkspaceStyle.DIM);
				g.drawString(shortcut, component.getWidth() - 8 - g.getFontMetrics().stringWidth(shortcut), 17);
				g.dispose();
			}
		});
		item.setOpaque(false);
		item.setBorderPainted(false);
		item.setPreferredSize(new Dimension(width - 10, 26));
		item.addActionListener(action);
		item.addActionListener(e -> item.repaint());
		add(item);
	}
}
