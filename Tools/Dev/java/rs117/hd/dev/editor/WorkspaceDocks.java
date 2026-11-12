package rs117.hd.dev.editor;

import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

public final class WorkspaceDocks extends JPanel {
	private final List<JComponent> left = new ArrayList<>();
	private final List<JComponent> right = new ArrayList<>();
	private final JComponent viewport;
	private final JComponent primaryPanel;
	private JSplitPane leftSplit;
	private JSplitPane rightSplit;
	private boolean initializeRightWidth;
	private int leftWidth = 300;
	private int rightWidth = 330;
	private List<JComponent> target;
	private int insertion;
	private Point dragPoint;
	private String dragTitle;

	public enum Side { LEFT, RIGHT }

	private final boolean resizable;

	private WorkspaceDocks(Builder builder) {
		super(new BorderLayout());
		viewport = builder.viewport;
		primaryPanel = builder.primaryPanel;
		resizable = builder.resizable;
		leftWidth = builder.leftWidth;
		rightWidth = builder.rightWidth;
		left.addAll(builder.left);
		right.addAll(builder.right);
		for (DragHandle panel : builder.headers)
			draggable(panel.header, panel.content, panel.title);
		rebuild();
	}

	public static Builder builder(JComponent viewport) { return new Builder(viewport); }

	private static final class DragHandle {
		final JComponent header;
		final JComponent content;
		final String title;

		DragHandle(JComponent header, JComponent content, String title) {
			this.header = header;
			this.content = content;
			this.title = title;
		}
	}

	public static final class Builder {
		private final JComponent viewport;
		private final List<JComponent> left = new ArrayList<>();
		private final List<JComponent> right = new ArrayList<>();
		private final List<DragHandle> headers = new ArrayList<>();
		private JComponent primaryPanel;
		private int leftWidth = 300;
		private int rightWidth = 330;
		private boolean resizable = true;

		private Builder(JComponent viewport) { this.viewport = viewport; }

		public Builder panel(JComponent panel, Side side) {
			(side == Side.LEFT ? left : right).add(panel);
			return this;
		}

		public Builder primaryPanel(JComponent panel, Side side) {
			primaryPanel = panel;
			return panel(panel, side);
		}

		public Builder movable(JComponent panel, JComponent handle, String title) {
			headers.add(new DragHandle(handle, panel, title));
			return this;
		}

		public Builder width(Side side, int width) {
			if (side == Side.LEFT)
				leftWidth = width;
			else
				rightWidth = width;
			return this;
		}

		public Builder resizable(boolean value) { resizable = value; return this; }
		public WorkspaceDocks build() { return new WorkspaceDocks(this); }
	}

	public void setPrimaryPanelVisible(boolean visible) {
		if (primaryPanel == null)
			return;
		rememberWidths();
		primaryPanel.setVisible(visible);
		rebuild();
	}

	public void draggable(JComponent header, JComponent panel, String title) {
		header.setCursor(WorkspaceStyle.cursor("grab"));
		var drag = new MouseAdapter() {
			private Point start;

			@Override public void mousePressed(MouseEvent e) {
				if (SwingUtilities.isLeftMouseButton(e))
					start = SwingUtilities.convertPoint(header, e.getPoint(), WorkspaceDocks.this);
			}

			@Override public void mouseDragged(MouseEvent e) {
				if (start == null)
					return;
				Point point = SwingUtilities.convertPoint(header, e.getPoint(), WorkspaceDocks.this);
				if (dragPoint == null && point.distance(start) < 5)
					return;
				dragPoint = point;
				dragTitle = title;
				Rectangle l = bounds(left, true);
				Rectangle r = bounds(right, false);
				target = l.contains(point) ? left : r.contains(point) ? right : null;
				insertion = 0;
				if (target != null) {
					for (JComponent item : target) {
						if (item.isVisible()) {
							Rectangle area = SwingUtilities.convertRectangle(item.getParent(), item.getBounds(), WorkspaceDocks.this);
							if (point.y < area.y + area.height / 2)
								break;
						}
						insertion++;
					}
				}
				repaint();
			}

			@Override public void mouseReleased(MouseEvent e) {
				start = null;
				if (dragPoint == null)
					return;
				if (target != null) {
					rememberWidths();
					int previous = target.indexOf(panel);
					left.remove(panel);
					right.remove(panel);
					target.add(previous >= 0 && previous < insertion ? insertion - 1 : insertion, panel);
					rebuild();
				}
				dragPoint = null;
				target = null;
				repaint();
			}
		};
		header.addMouseListener(drag);
		header.addMouseMotionListener(drag);
	}

	private Rectangle bounds(List<JComponent> dock, boolean isLeft) {
		for (JComponent item : dock) {
			if (item.isVisible() && item.getParent() != null) {
				Rectangle rect = SwingUtilities.convertRectangle(item.getParent(), item.getBounds(), this);
				return new Rectangle(rect.x, 0, rect.width, getHeight());
			}
		}
		return new Rectangle(isLeft ? 0 : getWidth() - 90, 0, 90, getHeight());
	}

	private void rememberWidths() {
		if (leftSplit != null && leftSplit.getDividerLocation() >= 220)
			leftWidth = leftSplit.getDividerLocation();
		if (rightSplit != null && rightSplit.getWidth() - rightSplit.getDividerLocation() - 4 >= 220)
			rightWidth = rightSplit.getWidth() - rightSplit.getDividerLocation() - 4;
	}

	private void rebuild() {
		removeAll();
		Component l = stack(left);
		Component r = stack(right);
		if (r != null)
			r.setPreferredSize(new Dimension(rightWidth, 420));
		rightSplit = r == null ? null : split(JSplitPane.HORIZONTAL_SPLIT, viewport, r, 1);
		Component center = rightSplit == null ? viewport : rightSplit;
		leftSplit = l == null ? null : split(JSplitPane.HORIZONTAL_SPLIT, l, center, 0);
		add(leftSplit == null ? center : leftSplit);
		if (leftSplit != null)
			leftSplit.setDividerLocation(leftWidth);
		revalidate();
		initializeRightWidth = rightSplit != null;
		repaint();
	}

	@Override public void doLayout() {
		super.doLayout();
		if (!initializeRightWidth || getWidth() <= 0)
			return;
		if (leftSplit != null)
			leftSplit.doLayout();
		if (rightSplit.getWidth() > rightWidth + 4) {
			initializeRightWidth = false;
			rightSplit.setDividerLocation(rightSplit.getWidth() - rightWidth - 4);
			rightSplit.doLayout();
		}
	}

	private Component stack(List<JComponent> dock) {
		Component result = null;
		for (int i = dock.size() - 1; i >= 0; i--) {
			JComponent panel = dock.get(i);
			if (!panel.isVisible())
				continue;
			panel.setMinimumSize(new Dimension(220, 70));
			if (result == null) {
				result = panel;
			} else {
				JSplitPane split = split(JSplitPane.VERTICAL_SPLIT, panel, result, 0.4);
				split.setPreferredSize(new Dimension(330, 420));
				split.setDividerLocation(getHeight() > 0 ? (int) (getHeight() * 0.4) : 335);
				result = split;
			}
		}
		return result;
	}

	private JSplitPane split(int orientation, Component first, Component second, double weight) {
		var split = new JSplitPane(orientation, first, second);
		WorkspaceStyle.divider(split);
		split.setEnabled(resizable);
		split.setResizeWeight(weight);
		return split;
	}

	@Override protected void paintChildren(Graphics graphics) {
		super.paintChildren(graphics);
		if (dragPoint == null)
			return;
		Graphics2D g = (Graphics2D) graphics.create();
		for (List<JComponent> dock : List.of(left, right)) {
			Rectangle area = bounds(dock, dock == left);
			g.setColor(new Color(71, 114, 179, dock == target ? 45 : 15));
			g.fill(area);
			g.setColor(WorkspaceStyle.ACCENT);
			g.drawRect(area.x, area.y, area.width - 1, area.height - 1);
			if (dock.stream().noneMatch(Component::isVisible)) {
				g.setFont(WorkspaceStyle.UI.deriveFont(11f));
				g.drawString(dock == left ? "Dock left" : "Dock right", area.x + 12, 22);
			}
			if (dock == target) {
				int y = area.height - 4;
				for (int i = insertion; i < dock.size(); i++) {
					var panel = dock.get(i);
					if (panel.isVisible()) {
						y = SwingUtilities.convertPoint(panel, 0, 0, this).y;
						break;
					}
				}
				g.fillRoundRect(area.x + 2, y, area.width - 4, 4, 4, 4);
			}
		}
		g.setFont(WorkspaceStyle.UI);
		int x = dragPoint.x + 12;
		int y = dragPoint.y + 8;
		int width = g.getFontMetrics().stringWidth(dragTitle) + 20;
		g.setColor(WorkspaceStyle.HEADER);
		g.fillRoundRect(x, y, width, 24, 8, 8);
		g.setColor(WorkspaceStyle.ACCENT);
		g.drawRoundRect(x, y, width, 24, 8, 8);
		g.setColor(WorkspaceStyle.TEXT);
		g.drawString(dragTitle, x + 10, y + 16);
		g.dispose();
	}
}
