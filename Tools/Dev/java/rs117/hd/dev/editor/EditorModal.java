package rs117.hd.dev.editor;

import java.awt.*;
import javax.swing.*;

public final class EditorModal {
	private EditorModal() {}

	public static Builder builder(JFrame owner, String title) { return new Builder(owner, title); }

	public static final class Builder {
		private final JFrame owner;
		private final String title;
		private JComponent content;
		private Dimension size;

		private Builder(JFrame owner, String title) { this.owner = owner; this.title = title; }
		public Builder content(JComponent content) { this.content = content; return this; }
		public Builder size(int width, int height) { size = new Dimension(width, height); return this; }

		public JDialog build() {
			var dialog = create(owner, title);
			if (content != null)
				dialog.setContentPane(content);
			if (size == null)
				dialog.pack();
			else
				dialog.setSize(size);
			dialog.setLocationRelativeTo(owner);
			return dialog;
		}
	}

	public static JDialog create(JFrame owner, String title) {
		var dialog = new JDialog(owner, title, Dialog.ModalityType.MODELESS);
		dialog.setUndecorated(true);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		
		var backdrop = new JComponent() {
			@Override protected void paintComponent(Graphics g) {
				g.setColor(new Color(10, 10, 10, 128));
				g.fillRect(0, 0, getWidth(), getHeight());
				if (!dialog.isVisible() || !isShowing())
					return;
				var shadow = (Graphics2D) g.create();
				shadow.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Point origin = getLocationOnScreen();
				int x = dialog.getX() - origin.x;
				int y = dialog.getY() - origin.y + 12;
				for (int spread = 28; spread > 0; spread -= 2) {
					shadow.setColor(new Color(0, 0, 0, 9));
					shadow.fillRoundRect(x - spread, y - spread, dialog.getWidth() + spread * 2,
						dialog.getHeight() + spread * 2, 16 + spread * 2, 16 + spread * 2);
				}
				shadow.dispose();
			}
		};
		backdrop.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mousePressed(java.awt.event.MouseEvent e) { dialog.dispose(); }
		});
		dialog.addComponentListener(new java.awt.event.ComponentAdapter() {
			private Component previousGlass;
			private boolean previouslyVisible;

			@Override public void componentShown(java.awt.event.ComponentEvent event) {
				if (!dialog.isVisible())
					return;
				previousGlass = owner.getGlassPane();
				previouslyVisible = previousGlass.isVisible();
				owner.setGlassPane(backdrop);
				backdrop.setVisible(true);
			}

			@Override public void componentHidden(java.awt.event.ComponentEvent event) {
				if (previousGlass != null && owner.getGlassPane() == backdrop) {
					owner.setGlassPane(previousGlass);
					previousGlass.setVisible(previouslyVisible);
				}
			}
		});
		dialog.getRootPane().setBorder(new javax.swing.border.LineBorder(WorkspaceStyle.BORDER, 1, true));
		dialog.addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override public void componentResized(java.awt.event.ComponentEvent e) {
				if (dialog.getGraphicsConfiguration().getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT))
					dialog.setShape(new java.awt.geom.RoundRectangle2D.Float(0, 0, dialog.getWidth(), dialog.getHeight(), 16, 16));
				backdrop.repaint();
			}
		});
		dialog.getContentPane().setBackground(WorkspaceStyle.MENU);
		dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
		return dialog;
	}

}
