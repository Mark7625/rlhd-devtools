package rs117.hd.dev.editor;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.*;

/** Floating utility window. Docked panels use WorkspaceDocks instead. */
public final class EditorPopup {
	private EditorPopup() {}

	public enum Anchor { CENTER, LEFT, RIGHT }

	public static Builder builder(Window owner, String title) { return new Builder(owner, title); }

	public static final class Builder {
		private final Window owner;
		private final String title;
		private JComponent content = new JPanel();
		private Anchor anchor = Anchor.CENTER;
		private Dimension size = new Dimension(420, 300);
		private Dimension minimum = new Dimension(180, 100);
		private boolean movable = true;
		private boolean resizable = true;

		private Builder(Window owner, String title) { this.owner = owner; this.title = title; }
		public Builder content(JComponent content) { this.content = content; return this; }
		public Builder anchor(Anchor anchor) { this.anchor = anchor; return this; }
		public Builder size(int width, int height) { size = new Dimension(width, height); return this; }
		public Builder minimumSize(int width, int height) { minimum = new Dimension(width, height); return this; }
		public Builder movable(boolean value) { movable = value; return this; }
		public Builder resizable(boolean value) { resizable = value; return this; }

		public JDialog build() {
			var dialog = new JDialog(owner, title);
			dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
			dialog.setIconImages(EditorSplash.appIcons());
			dialog.setUndecorated(!movable);
			dialog.setResizable(resizable);
			dialog.setMinimumSize(minimum);
			if (movable) {
				dialog.setContentPane(content);
			} else {
				var root = new JPanel(new BorderLayout());
				root.setBackground(WorkspaceStyle.PANEL);
				root.setBorder(BorderFactory.createLineBorder(WorkspaceStyle.BORDER));
				var header = new JPanel(new BorderLayout());
				header.setBackground(WorkspaceStyle.HEADER);
				header.add(EditorText.builder(title).font(WorkspaceStyle.SEMIBOLD)
					.border(BorderFactory.createEmptyBorder(0, 8, 0, 0)).build());
				header.add(EditorButton.builder().style(WorkspaceStyle.Button.TAB)
					.icon("close", 12).tooltip("Close").size(24, 24).onClick(dialog::dispose).build(), BorderLayout.EAST);
				root.add(header, BorderLayout.NORTH);
				root.add(content);
				if (resizable)
					root.add(resizeHandle(dialog), BorderLayout.SOUTH);
				dialog.setContentPane(root);
			}
			dialog.setSize(Math.max(size.width, minimum.width), Math.max(size.height, minimum.height));
			dialog.setLocationRelativeTo(owner);
			if (owner != null && anchor != Anchor.CENTER) {
				Rectangle bounds = owner.getBounds();
				dialog.setLocation(anchor == Anchor.LEFT ? bounds.x : bounds.x + bounds.width - dialog.getWidth(), dialog.getY());
			}
			return dialog;
		}
	}

	private static JComponent resizeHandle(JDialog dialog) {
		var handle = new JLabel(WorkspaceStyle.icon("grip", 10), SwingConstants.RIGHT);
		handle.setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
		var drag = new MouseAdapter() {
			private Point origin;
			private Dimension size;

			@Override public void mousePressed(MouseEvent event) {
				if (SwingUtilities.isLeftMouseButton(event)) {
					origin = event.getLocationOnScreen();
					size = dialog.getSize();
				}
			}

			@Override public void mouseDragged(MouseEvent event) {
				if (origin == null)
					return;
				var min = dialog.getMinimumSize();
				dialog.setSize(Math.max(min.width, size.width + event.getXOnScreen() - origin.x),
					Math.max(min.height, size.height + event.getYOnScreen() - origin.y));
			}

			@Override public void mouseReleased(MouseEvent event) { origin = null; }
		};
		handle.addMouseListener(drag);
		handle.addMouseMotionListener(drag);
		return handle;
	}
}
