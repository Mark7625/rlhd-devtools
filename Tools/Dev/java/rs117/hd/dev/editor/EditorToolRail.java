package rs117.hd.dev.editor;

import java.awt.Component;
import java.awt.Dimension;
import java.util.List;
import javax.swing.*;

/** Vertical rail whose tool groups are supplied by the active workspace. */
public final class EditorToolRail extends JPanel {
	public EditorToolRail() {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(WorkspaceStyle.BAR);
		setBorder(BorderFactory.createEmptyBorder(8, 6, 0, 6));
		setPreferredSize(new Dimension(44, 0));
	}

	public void setGroups(List<List<AbstractButton>> groups) {
		removeAll();
		boolean previousGroup = false;
		for (List<AbstractButton> group : groups) {
			if (group.isEmpty())
				continue;
			if (previousGroup) {
				var divider = new JPanel();
				divider.setBackground(WorkspaceStyle.BORDER);
				divider.setMaximumSize(new Dimension(22, 1));
				add(divider);
				add(Box.createVerticalStrut(4));
			}
			for (AbstractButton tool : group) {
				if (!WorkspaceStyle.hasButtonStyle(tool))
					WorkspaceStyle.button(tool, WorkspaceStyle.Button.TOOL);
				tool.setAlignmentX(Component.CENTER_ALIGNMENT);
				tool.setMaximumSize(new Dimension(32, 32));
				add(tool);
				add(Box.createVerticalStrut(4));
			}
			previousGroup = true;
		}
		revalidate();
		repaint();
	}
}
