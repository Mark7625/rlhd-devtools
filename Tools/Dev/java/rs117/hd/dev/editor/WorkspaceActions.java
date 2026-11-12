package rs117.hd.dev.editor;

import com.google.gson.*;
import java.awt.*;
import java.nio.file.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import javax.swing.*;

public final class WorkspaceActions {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
	private final JFrame owner;
	private final Supplier<JsonObject> capture;
	private final java.util.function.Consumer<JsonObject> restore;
	private final JLabel status;
	private final Path draft = Path.of(System.getProperty("user.home"), ".runelite", "117hd-devtools", "draft.json");
	private final WorkspaceHistory history = new WorkspaceHistory();
	private final List<Command> commands = new ArrayList<>();
	private final List<Supplier<List<Command>>> commandSources = new ArrayList<>();
	private final JButton undo = button("undo", null);
	private final JButton redo = button("redo", null);
	private final JButton steps = button("history", "0");
	private final Timer autosave = new Timer(30_000, e -> saveDraft(false));
	private boolean changed;
	private boolean restoring;
	private JDialog palette;
	public final JLabel draftStatus = new JLabel("Autosave on · 30s");

	public static final class Command {
		final String group, label, keys;
		final Runnable run;
		final Supplier<Color> color;

		public Command(String group, String label, String keys, Runnable run) {
			this(group, label, keys, run, () -> null);
		}

		public Command(String group, String label, String keys, Runnable run, Supplier<Color> color) {
			this.group = group;
			this.label = label;
			this.keys = keys;
			this.run = run;
			this.color = color;
		}

		@Override public String toString() { return group + "    " + label + (keys.isEmpty() ? "" : "    " + keys); }
	}

	public WorkspaceActions(JFrame owner, Supplier<JsonObject> capture, java.util.function.Consumer<JsonObject> restore, JLabel status) {
		this.owner = owner;
		this.capture = capture;
		this.restore = restore;
		this.status = status;
		register("Edit", "Undo", "Ctrl Z", "ctrl Z", () -> jump(history.index() - 1, "Undo"));
		register("Edit", "Redo", "Ctrl Shift Z", "ctrl shift Z", () -> jump(history.index() + 1, "Redo"));
		bind("ctrl Y", () -> jump(history.index() + 1, "Redo"), false);
		register("Edit", "Show history", "", null, this::showHistory);
		register("File", "Autosave draft now", "", null, () -> saveDraft(true));
		register("File", "Restore autosaved draft", "", null, this::restoreDraft);
		bind("ctrl K", this::showPalette, true);
		register("Window", "Keyboard shortcuts", "?", "typed ?", this::showShortcuts);
		updateHistory();
	}

	public void addCommandSource(Supplier<List<Command>> source) { commandSources.add(source); }

	public void start() { autosave.start(); }
	public void stop() { autosave.stop(); }

	public void reset(String entity) {
		history.reset("Opened " + entity, capture.get());
		updateHistory();
	}

	public void edit(String label, Runnable action) {
		if (restoring) {
			action.run();
			return;
		}
		JsonObject before = capture.get();
		action.run();
		JsonObject after = capture.get();
		history.add(label, before, after);
		changed |= !before.equals(after);
		updateHistory();
	}

	private void jump(int index, String operation) {
		if (index < 0 || index >= history.size() || index == history.index())
			return;
		String label = history.get(index < history.index() ? history.index() : index).label;
		try {
			restoreState(history.get(index).state.getAsJsonObject());
			history.moveTo(index);
			changed = true;
			status.setText(operation + ": " + label);
		} catch (RuntimeException ex) {
			status.setText("Could not restore history: " + ex.getMessage());
		}
		updateHistory();
	}

	private void restoreState(JsonObject data) {
		restoring = true;
		try {
			restore.accept(data);
		} finally {
			restoring = false;
		}
	}

	private void updateHistory() {
		undo.setEnabled(history.canUndo());
		redo.setEnabled(history.canRedo());
		undo.setToolTipText(history.canUndo() ? "Undo " + history.get(history.index()).label + " (Ctrl+Z)" : "Nothing to undo");
		redo.setToolTipText(history.canRedo() ? "Redo " + history.get(history.index() + 1).label + " (Ctrl+Shift+Z)" : "Nothing to redo");
		steps.setText(String.valueOf(Math.max(0, history.size() - 1)));
	}

	public JPanel toolbar() {
		var panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
		panel.setOpaque(false);
		undo.addActionListener(e -> jump(history.index() - 1, "Undo"));
		redo.addActionListener(e -> jump(history.index() + 1, "Redo"));
		steps.addActionListener(e -> showHistory());
		panel.add(undo);
		panel.add(redo);
		panel.add(steps);
		var search = new JButton() {
			@Override protected void paintComponent(Graphics graphics) {
				var g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(new Color(0x1A1A1A));
				g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
				g.setColor(new Color(getModel().isRollover() ? 0x4A4A4A : 0x333333));
				g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
				WorkspaceStyle.icon("search", 12).paintIcon(this, g, 8, 6);
				paintKeycap(g, "Ctrl K", 29, 4, 45, 16);
				g.dispose();
			}
		};
		WorkspaceStyle.button(search, WorkspaceStyle.Button.TAB);
		search.setPreferredSize(new Dimension(79, 24));
		search.setToolTipText("Command search (Ctrl+K)");
		search.getAccessibleContext().setAccessibleName("Command search");
		search.addActionListener(e -> showPalette());
		panel.add(search);
		var help = button(null, "?");
		help.setPreferredSize(new Dimension(26, 26));
		help.addActionListener(e -> showShortcuts());
		panel.add(help);
		return panel;
	}

	private static JButton button(String icon, String text) {
		var button = new JButton(text, icon == null ? null : WorkspaceStyle.icon(icon, 14));
		WorkspaceStyle.button(button, WorkspaceStyle.Button.TAB);
		if (text == null)
			button.setPreferredSize(new Dimension(26, 26));
		return button;
	}

	public void register(String group, String label, String keys, String binding, Runnable run) {
		commands.add(new Command(group, label, keys, run));
		if (binding != null)
			bind(binding, run, false);
	}

	private void bind(String binding, Runnable action, boolean allowText) {
		owner.getRootPane().registerKeyboardAction(e -> {
			if (allowText || !(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent))
				action.run();
		}, KeyStroke.getKeyStroke(binding), JComponent.WHEN_IN_FOCUSED_WINDOW);
	}

	private void showHistory() {
		var menu = new WorkspaceMenu(280);
		var title = new JLabel("History");
		title.setFont(WorkspaceStyle.SEMIBOLD);
		title.setForeground(WorkspaceStyle.TEXT);
		var count = new JLabel(history.index() + " steps · " + Math.max(0, history.size() - history.index() - 1) + " redo");
		count.setFont(WorkspaceStyle.MONO.deriveFont(10f));
		count.setForeground(WorkspaceStyle.DIM);
		var header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));
		header.add(title, BorderLayout.WEST);
		header.add(count, BorderLayout.EAST);
		menu.add(header);
		var rows = new DefaultListModel<WorkspaceHistory.Step>();
		for (int i = history.size() - 1; i >= 0; i--)
			rows.addElement(history.get(i));
		var list = new JList<>(rows);
		list.setFixedCellHeight(24);
		list.setCellRenderer((source, step, index, selected, focus) -> {
			int position = history.size() - index - 1;
			boolean current = position == history.index();
			var row = new JPanel(new BorderLayout(8, 0));
			row.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
			row.setBackground(current ? WorkspaceStyle.ACCENT : selected ? WorkspaceStyle.BORDER : WorkspaceStyle.MENU);
			var label = new JLabel(step.label, WorkspaceStyle.badge("", current ? Color.WHITE : WorkspaceStyle.DIM), SwingConstants.LEFT);
			label.setFont(WorkspaceStyle.UI);
			label.setForeground(current ? Color.WHITE : position > history.index() ? new Color(0x6A6A6A) : WorkspaceStyle.TEXT);
			row.add(label);
			var time = new JLabel(TIME.format(step.time));
			time.setFont(WorkspaceStyle.MONO.deriveFont(10f));
			time.setForeground(current ? new Color(0xCFE0FF) : WorkspaceStyle.DIM);
			row.add(time, BorderLayout.EAST);
			return row;
		});
		list.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mouseClicked(java.awt.event.MouseEvent e) {
				int row = list.locationToIndex(e.getPoint());
				if (row >= 0) {
					menu.setVisible(false);
					jump(history.size() - row - 1, "History");
				}
			}
		});
		var scroll = WorkspaceStyle.listScroll(list);
		scroll.setPreferredSize(new Dimension(270, Math.min(320, rows.size() * 24)));
		menu.add(scroll);
		var footer = new JLabel("Click a step to jump to it · Ctrl+Z / Ctrl+Shift+Z");
		footer.setFont(WorkspaceStyle.UI.deriveFont(11f));
		footer.setForeground(WorkspaceStyle.DIM);
		footer.setBorder(BorderFactory.createEmptyBorder(6, 6, 2, 6));
		menu.add(footer);
		menu.show(steps, steps.getWidth() - 280, steps.getHeight() + 4);
	}
	private void showPalette() {
		if (palette != null && palette.isVisible()) {
			palette.dispose();
			return;
		}
		var dialog = palette = EditorModal.create(owner, "Command search");
		var input = WorkspaceStyle.search("Type a command, definition or light…");
		input.setBorder(BorderFactory.createEmptyBorder());
		input.setOpaque(false);
		input.setFont(WorkspaceStyle.UI.deriveFont(14f));
		input.setForeground(new Color(0xE6E6E6));
		input.setCaretColor(WorkspaceStyle.TEXT);
		var inputRow = new JPanel(new BorderLayout(10, 0));
		inputRow.setBackground(WorkspaceStyle.MENU);
		inputRow.setPreferredSize(new Dimension(560, 44));
		inputRow.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x2A2A2A)),
			BorderFactory.createEmptyBorder(0, 14, 0, 14)));
		inputRow.add(new JLabel(WorkspaceStyle.icon("search", 14)), BorderLayout.WEST);
		inputRow.add(input);
		inputRow.add(keycap("Esc", 20), BorderLayout.EAST);
		var rows = new DefaultListModel<Command>();
		var list = new JList<>(rows);
		list.setFixedCellHeight(31);
		list.setCellRenderer((source, command, index, selected, focus) -> commandRow(command, selected));
		list.setFont(WorkspaceStyle.UI);
		var scroll = WorkspaceStyle.listScroll(list);
		scroll.setPreferredSize(new Dimension(560, 368));
		scroll.setBorder(BorderFactory.createEmptyBorder(4, 4, 0, 4));
		scroll.setBackground(WorkspaceStyle.MENU);
		var scrollbar = scroll.getVerticalScrollBar();
		scrollbar.setPreferredSize(new Dimension(12, 0));
		scrollbar.setUI(new javax.swing.plaf.basic.BasicScrollBarUI() {
			@Override protected JButton createDecreaseButton(int direction) { return scrollArrow(direction); }
			@Override protected JButton createIncreaseButton(int direction) { return scrollArrow(direction); }

			@Override protected void paintTrack(Graphics g, JComponent c, Rectangle bounds) {
				g.setColor(WorkspaceStyle.HEADER);
				g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
			}

			@Override protected void paintThumb(Graphics graphics, JComponent c, Rectangle bounds) {
				if (bounds.isEmpty() || !scrollbar.isEnabled())
					return;
				var g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(new Color(isThumbRollover() ? 0xB8B8B8 : 0x969696));
				g.fillRoundRect(bounds.x + 2, bounds.y, bounds.width - 4, bounds.height, 8, 8);
				g.dispose();
			}
		});
		scroll.getViewport().setBackground(WorkspaceStyle.MENU);
		list.setBackground(WorkspaceStyle.MENU);
		var resultCount = muted("");
		var footer = new JPanel(new BorderLayout());
		footer.setBackground(WorkspaceStyle.MENU);
		footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0x2A2A2A)),
			BorderFactory.createEmptyBorder(6, 14, 6, 14)));
		footer.add(muted("↑ ↓ move     Enter run     Esc close"));
		footer.add(resultCount, BorderLayout.EAST);
		var empty = muted("No commands match.");
		empty.setBorder(BorderFactory.createEmptyBorder(14, 14, 0, 14));
		empty.setVerticalAlignment(SwingConstants.TOP);
		var results = new JPanel(new CardLayout());
		results.setBackground(WorkspaceStyle.MENU);
		results.add(scroll, "list");
		results.add(empty, "empty");
		Runnable filter = () -> {
			rows.clear();
			String[] words = input.getText().strip().toLowerCase(Locale.ROOT).split("\\s+");
			var candidates = new ArrayList<>(commands);
			if (!hasDraft())
				candidates.removeIf(command -> command.label.equals("Restore autosaved draft"));
			for (var source : commandSources)
				candidates.addAll(source.get());
			candidates.sort(java.util.Comparator.comparingInt(WorkspaceActions::commandOrder));
			for (Command command : candidates) {
				String haystack = (command.group + " " + command.label).toLowerCase(Locale.ROOT);
				if (java.util.Arrays.stream(words).allMatch(haystack::contains))
					rows.addElement(command);
				if (rows.size() == 60)
					break;
			}
			if (!rows.isEmpty())
				list.setSelectedIndex(0);
			resultCount.setText(rows.size() + (rows.size() == 1 ? " result" : " results"));
			((CardLayout) results.getLayout()).show(results, rows.isEmpty() ? "empty" : "list");
		};
		input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) { filter.run(); }
			public void removeUpdate(javax.swing.event.DocumentEvent e) { filter.run(); }
			public void changedUpdate(javax.swing.event.DocumentEvent e) { filter.run(); }
		});
		Runnable run = () -> {
			Command selected = list.getSelectedValue();
			if (selected != null) {
				dialog.dispose();
				SwingUtilities.invokeLater(selected.run);
			}
		};
		input.addActionListener(e -> run.run());
		for (int direction : new int[] { -1, 1 }) {
			input.registerKeyboardAction(e -> {
				if (rows.isEmpty())
					return;
				int index = Math.floorMod(list.getSelectedIndex() + direction, rows.size());
				list.setSelectedIndex(index);
				list.ensureIndexIsVisible(index);
			}, KeyStroke.getKeyStroke(direction < 0 ? "UP" : "DOWN"), JComponent.WHEN_FOCUSED);
		}
		list.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
			@Override public void mouseMoved(java.awt.event.MouseEvent e) {
				int index = list.locationToIndex(e.getPoint());
				var bounds = index < 0 ? null : list.getCellBounds(index, index);
				if (bounds != null && bounds.contains(e.getPoint()))
					list.setSelectedIndex(index);
			}
		});
		list.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mouseClicked(java.awt.event.MouseEvent e) {
				int index = list.locationToIndex(e.getPoint());
				var bounds = index < 0 ? null : list.getCellBounds(index, index);
				if (bounds != null && bounds.contains(e.getPoint())) {
					list.setSelectedIndex(index);
					run.run();
				}
			}
		});
		dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), KeyStroke.getKeyStroke("ctrl K"), JComponent.WHEN_IN_FOCUSED_WINDOW);
		dialog.add(inputRow, BorderLayout.NORTH);
		dialog.add(results);
		dialog.add(footer, BorderLayout.SOUTH);
		filter.run();
		dialog.pack();
		dialog.setLocation(owner.getX() + (owner.getWidth() - dialog.getWidth()) / 2, owner.getY() + owner.getHeight() * 12 / 100);
		dialog.setVisible(true);
		input.requestFocusInWindow();
	}

	private static int commandOrder(Command command) {
		switch (command.group) {
			case "Edit": return 0;
			case "File":
				switch (command.label) {
					case "Save lights": return 10;
					case "Save placement": return 11;
					case "Save definitions": return 12;
					default: return 13;
				}
			case "Tool": return 20;
			case "View": return 30;
			case "Window": return 40;
			case "Light": return 50;
			default: return 60;
		}
	}

	private static JButton scrollArrow(int direction) {
		var button = new JButton() {
			@Override protected void paintComponent(Graphics graphics) {
				var g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(WorkspaceStyle.HEADER);
				g.fillRect(0, 0, getWidth(), getHeight());
				g.setColor(new Color(0x969696));
				int x = getWidth() / 2;
				int y = getHeight() / 2;
				int tip = direction == SwingConstants.NORTH ? -3 : 3;
				g.fillPolygon(new int[] {x - 4, x, x + 4}, new int[] {y - tip, y + tip, y - tip}, 3);
				g.dispose();
			}
		};
		button.setPreferredSize(new Dimension(12, 14));
		button.setBorderPainted(false);
		button.setFocusable(false);
		return button;
	}

	private JPanel commandRow(Command command, boolean selected) {
		var row = new JPanel(new BorderLayout(10, 0)) {
			@Override protected void paintComponent(Graphics graphics) {
				if (!selected)
					return;
				var g = (Graphics2D) graphics.create();
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setColor(new Color(0x334D73));
				g.fillRoundRect(0, 0, getWidth(), getHeight() - 1, 10, 10);
				g.dispose();
			}
		};
		row.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
		row.setOpaque(false);
		var group = new JLabel(command.group.toUpperCase(Locale.ROOT));
		group.setFont(WorkspaceStyle.UI.deriveFont(10f));
		group.setForeground(selected ? new Color(0xCFE0FF) : new Color(0x7D7D7D));
		group.setPreferredSize(new Dimension(72, 30));
		row.add(group, BorderLayout.WEST);
		Color color = command.color.get();
		var label = new JLabel(command.label, color == null ? null : WorkspaceStyle.badge("", color), SwingConstants.LEFT);
		label.setFont(WorkspaceStyle.UI);
		label.setForeground(selected ? Color.WHITE : WorkspaceStyle.TEXT);
		row.add(label);
		if (!command.keys.isEmpty())
			row.add(keycaps(command.keys), BorderLayout.EAST);
		return row;
	}

	private JPanel keycaps(String keys) {
		var panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 0));
		panel.setOpaque(false);
		for (String key : keys.split(" "))
			panel.add(keycap(key.replace('_', ' '), 20));
		var centered = new JPanel(new GridBagLayout());
		centered.setOpaque(false);
		centered.add(panel);
		return centered;
	}

	private JComponent keycap(String key, int height) {
		return new JComponent() {
			@Override public Dimension getPreferredSize() {
				return new Dimension(Math.max(18, getFontMetrics(WorkspaceStyle.MONO.deriveFont(10f)).stringWidth(key) + 10), height);
			}

			@Override protected void paintComponent(Graphics graphics) {
				var g = (Graphics2D) graphics.create();
				paintKeycap(g, key, 0, (getHeight() - height) / 2, getWidth(), height);
				g.dispose();
			}
		};
	}

	private static void paintKeycap(Graphics2D g, String key, int x, int y, int width, int height) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setColor(WorkspaceStyle.BORDER);
		g.fillRoundRect(x, y, width, height, 8, 8);
		g.setColor(WorkspaceStyle.HEADER);
		g.fillRoundRect(x + 1, y + 1, width - 2, height - 3, 6, 6);
		g.setColor(new Color(0xD6D6D6));
		g.setFont(WorkspaceStyle.MONO.deriveFont(10f));
		var metrics = g.getFontMetrics();
		g.drawString(key, x + (width - metrics.stringWidth(key)) / 2, y + (height - metrics.getHeight()) / 2 + metrics.getAscent());
	}

	private static JLabel muted(String text) {
		var label = new JLabel(text);
		label.setFont(WorkspaceStyle.UI.deriveFont(11f));
		label.setForeground(new Color(0x7D7D7D));
		return label;
	}

	private JPanel shortcutGroup(String title, String[]... shortcuts) {
		var group = new JPanel(new BorderLayout());
		group.setOpaque(false);
		var heading = muted(title.toUpperCase(Locale.ROOT));
		heading.setFont(WorkspaceStyle.UI.deriveFont(10f));
		heading.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x2A2A2A)),
			BorderFactory.createEmptyBorder(0, 0, 6, 0)));
		group.add(heading, BorderLayout.NORTH);
		var rows = new JPanel(new GridLayout(0, 1));
		rows.setOpaque(false);
		for (String[] shortcut : shortcuts) {
			var row = new JPanel(new BorderLayout());
			row.setOpaque(false);
			row.setPreferredSize(new Dimension(300, 26));
			var label = new JLabel(shortcut[0]);
			label.setFont(WorkspaceStyle.UI);
			label.setForeground(WorkspaceStyle.TEXT);
			row.add(label);
			row.add(keycaps(shortcut[1]), BorderLayout.EAST);
			rows.add(row);
		}
		group.add(rows, BorderLayout.CENTER);
		var wrapper = new JPanel(new BorderLayout());
		wrapper.setOpaque(false);
		wrapper.add(group, BorderLayout.NORTH);
		return wrapper;
	}

	private void showShortcuts() {
		var dialog = EditorModal.create(owner, "Keyboard shortcuts");
		var rows = new JPanel(new net.miginfocom.swing.MigLayout("insets 0px 22px 22px 22px, gap 36px 20px, wrap 2", "[grow,fill][grow,fill]", "[top][top][top]"));
		rows.setBackground(WorkspaceStyle.MENU);
		rows.add(shortcutGroup("General", new String[]{"Command search", "Ctrl K"}, new String[]{"Keyboard shortcuts", "?"},
			new String[]{"Undo", "Ctrl Z"}, new String[]{"Redo", "Ctrl Shift Z"}, new String[]{"Redo (alt)", "Ctrl Y"},
			new String[]{"Close menu / clear group", "Esc"}));
		rows.add(shortcutGroup("Tools", new String[]{"Select", "Q"}, new String[]{"Place light", "P"},
			new String[]{"Remove light", "X"}, new String[]{"Vertex pick", "1"}, new String[]{"Face pick", "2"},
			new String[]{"Apply tool to group", "Enter"}));
		rows.add(shortcutGroup("Viewport", new String[]{"Orbit", "Drag"}, new String[]{"Pan", "Right_Drag"},
			new String[]{"Zoom", "Wheel"}, new String[]{"Box select", "Ctrl Drag"}, new String[]{"Frame all", "F"}));
		rows.add(shortcutGroup("Layout", new String[]{"Together", "T"}, new String[]{"Side by side", "S"},
			new String[]{"Single", "G"}));
		rows.add(shortcutGroup("Lights", new String[]{"Delete selected light", "Del"}, new String[]{"Mirror across X", "M"},
			new String[]{"Mirror across Z", "Shift M"}));
		rows.add(shortcutGroup("Definitions", new String[]{"Duplicate definition", "Ctrl D"}));
		var header = new JPanel(new BorderLayout());
		header.setBackground(WorkspaceStyle.MENU);
		header.setBorder(BorderFactory.createEmptyBorder(16, 22, 14, 22));
		var title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
		title.setOpaque(false);
		var heading = new JLabel("Keyboard shortcuts");
		heading.setFont(WorkspaceStyle.SEMIBOLD.deriveFont(15f));
		heading.setForeground(new Color(0xEEEEEE));
		title.add(heading);
		title.add(Box.createHorizontalStrut(10));
		title.add(muted("Press ? anytime"));
		header.add(title);
		var close = button("close", null);
		close.addActionListener(e -> dialog.dispose());
		header.add(close, BorderLayout.EAST);
		dialog.add(header, BorderLayout.NORTH);
		var scroll = new JScrollPane(rows);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(WorkspaceStyle.MENU);
		dialog.add(scroll);
		dialog.setSize(Math.min(760, owner.getWidth() - 40), Math.min(owner.getHeight() - 80, rows.getPreferredSize().height + header.getPreferredSize().height + 2));
		dialog.setLocationRelativeTo(owner);
		dialog.setVisible(true);
	}
	private void saveDraft(boolean manual) {
		if (!changed && !manual)
			return;
		try {
			Files.createDirectories(draft.getParent());
			Path temporary = Files.createTempFile(draft.getParent(), "draft-", ".tmp");
			try {
				JsonObject data = capture.get();
				data.addProperty("time", TIME.format(LocalTime.now()));
				Files.writeString(temporary, data.toString());
				Files.move(temporary, draft, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
				draftStatus.setText("Draft autosaved " + data.get("time").getAsString());
				changed = false;
			} finally {
				Files.deleteIfExists(temporary);
			}
		} catch (Exception ex) {
			status.setText("Draft autosave failed: " + ex.getMessage());
		}
	}

	public boolean hasDraft() { return Files.isRegularFile(draft); }

	public String draftTime() {
		try {
			return new Gson().fromJson(Files.readString(draft), JsonObject.class).get("time").getAsString();
		} catch (Exception ex) {
			return "an unknown time";
		}
	}

	public void restoreDraft() {
		if (!hasDraft())
			return;
		try {
			JsonObject data = new Gson().fromJson(Files.readString(draft), JsonObject.class);
			String time = data.remove("time").getAsString();
			edit("Restore draft " + time, () -> restoreState(data));
			status.setText("Restored draft " + time);
		} catch (Exception ex) {
			status.setText("Draft restore failed: " + ex.getMessage());
		}
	}
}
