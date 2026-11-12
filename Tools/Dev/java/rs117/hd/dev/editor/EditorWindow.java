package rs117.hd.dev.editor;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.function.Consumer;
import javax.swing.*;
import net.miginfocom.swing.MigLayout;

/** Window chrome shared by content editors. Content supplies its tabs, actions and body. */
public class EditorWindow extends JFrame {
	public EditorWindow() {
		super("117HD Editor");
		setIconImages(EditorSplash.appIcons());
		setMinimumSize(new Dimension(1100, 700));
		setSize(1456, 939);
	}

	protected final JPanel editorToolbar(JComponent leading, JComponent actions, JComponent saves,
		Consumer<Boolean> compactChanged, JButton... workspaceTabs) {
		var top = new JPanel(new BorderLayout(14, 0));
		top.setBackground(WorkspaceStyle.BAR);
		top.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
		top.setPreferredSize(new Dimension(0, 32));

		var brand = new JPanel(new MigLayout("insets 0, gap 8 0, aligny center", "[][][]14[]", "[24!]"));
		brand.setOpaque(false);
		var logo = new ImageIcon(EditorWindow.class.getResource("/rs117/hd/dev/editor/icons/editor-icon-48.png"));
		brand.add(new JLabel(new ImageIcon(logo.getImage().getScaledInstance(22, 22, Image.SCALE_SMOOTH))));
		var product = new JLabel("117HD");
		product.setFont(WorkspaceStyle.SEMIBOLD);
		product.setForeground(new Color(0xE6E6E6));
		brand.add(product);
		var editorName = new JLabel("Editor");
		editorName.setFont(WorkspaceStyle.UI);
		editorName.setForeground(new Color(0x7D7D7D));
		brand.add(editorName);
		brand.add(leading);
		top.add(brand, BorderLayout.WEST);

		var tabs = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
		tabs.setOpaque(false);
		for (JButton tab : workspaceTabs)
			tabs.add(tab);
		top.add(tabs, BorderLayout.CENTER);

		var right = new JPanel(new BorderLayout(6, 0));
		right.setOpaque(false);
		right.add(actions, BorderLayout.CENTER);
		right.add(saves, BorderLayout.EAST);
		top.add(right, BorderLayout.EAST);
		top.addComponentListener(new ComponentAdapter() {
			@Override public void componentResized(ComponentEvent event) {
				boolean compact = top.getWidth() < 1250;
				((BorderLayout) top.getLayout()).setHgap(compact ? 8 : 14);
				for (JButton tab : workspaceTabs) {
					int padding = compact ? 9 : 12;
					tab.setBorder(BorderFactory.createEmptyBorder(0, padding, 0, padding));
				}
				editorName.setVisible(!compact);
				compactChanged.accept(compact);
			}
		});
		return top;
	}

	protected final void installEditorContent(JComponent toolbar, JComponent body, JComponent footer) {
		var root = new JPanel(new BorderLayout(0, 1));
		root.setBackground(WorkspaceStyle.GAP);
		root.add(toolbar, BorderLayout.NORTH);
		root.add(body, BorderLayout.CENTER);
		root.add(footer, BorderLayout.SOUTH);
		setContentPane(root);
		setLocationRelativeTo(null);
	}
}
