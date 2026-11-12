package rs117.hd.dev.lights;

import rs117.hd.dev.editor.WorkspaceStyle;
import rs117.hd.dev.editor.WorkspaceDocks;
import rs117.hd.dev.editor.WorkspaceMenu;
import rs117.hd.dev.editor.WorkspaceActions;
import rs117.hd.dev.editor.EditorWindow;
import rs117.hd.dev.editor.EditorToolRail;
import rs117.hd.dev.editor.EditorSelect;
import rs117.hd.dev.editor.EditorText;
import rs117.hd.dev.editor.EditorButton;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import rs117.hd.scene.lights.Alignment;
import rs117.hd.scene.lights.LightDefinition;
import rs117.hd.scene.lights.LightType;
import rs117.hd.scene.model.ModelSnapshot;
import rs117.hd.utils.ColorUtils;

public class LightViewerFrame extends EditorWindow {
	public static final class AnchorSelection {
		public final int pieceIndex;
		public final int localIndex;
		public final boolean triangle;

		public AnchorSelection(int pieceIndex, int localIndex, boolean triangle) {
			this.pieceIndex = pieceIndex;
			this.localIndex = localIndex;
			this.triangle = triangle;
		}
	}

	public interface Callbacks {
		default com.google.gson.JsonObject captureEditorData() { return new com.google.gson.JsonObject(); }
		default void restoreEditorData(com.google.gson.JsonObject data) {}
		default void duplicateDefinition(String source, String name) {}
		default List<String> getOrderedDefinitionNames() { return getLightDescriptions(); }
		default void mirrorAnchor(AnchorSelection anchor, boolean zAxis) {}
		void refreshSnapshot();

		void refreshSightings();

		void pickClicked(ViewportPanel.Pick pick, ViewportPanel.PickAction action);

		void boxSelected(Set<Integer> globalIndices, boolean add, ViewportPanel.PickTarget target);

		void anchorSelected(AnchorSelection anchor);

		Set<Integer> litFaces(int pieceIndex);

		Set<Integer> litVertices(int pieceIndex);

		List<String> getLightDescriptions();

		void onLightBrushChanged(String description);

		void saveModelLights();

		void saveLightDefinitions();

		void loadPlayer(int playerId);

		void loadObject(int objectId);

		void loadNpc(int npcId);

		void loadGraphic(int graphicId);

		void playerViewSelected();

		void poseAnimation(int animId);

		List<LightMeshCapture.Sighting> getPlayerSightings();

		List<LightMeshCapture.Sighting> getGraphicSightings();

		List<LightMeshCapture.Sighting> getObjectSightings();

		List<LightMeshCapture.Sighting> getNpcSightings();

		String getPieceName(int pieceIndex);

		String getPieceItemIdsText(int pieceIndex);

		void applyPieceSettings(int pieceIndex, String name, String itemIdsText);

		void applyProfileOffset(int pieceIndex, float x, float y, float z);

		float[] getProfileOffset(int pieceIndex);

		String getPieceListLabel(int pieceIndex);

		@Nullable
		String getAnchorLightDescription(AnchorSelection anchor);

		void setAnchorLightDescription(AnchorSelection anchor, String description);

		@Nullable
		float[] getAnchorBarycentric(AnchorSelection anchor);

		void setAnchorBarycentric(AnchorSelection anchor, float b0, float b1, float b2);

		LightDefinition getDefinition(String description);

		void saveDefinition(String description, LightDefinition definition);

		void createDefinition(String description);

		void deleteDefinition(String description);
	}

	private static class Row {
		final int pieceIndex;
		final int sightingId;
		final List<Integer> equipmentParts;
		final int itemId;

		Row(int itemId, List<Integer> equipmentParts) {
			this(equipmentParts.get(0), -1, equipmentParts, itemId);
		}

		Row(int pieceIndex) {
			this(pieceIndex, -1);
		}

		Row(int pieceIndex, int sightingId) {
			this(pieceIndex, sightingId, List.of(), -1);
		}

		private Row(int pieceIndex, int sightingId, List<Integer> equipmentParts, int itemId) {
			this.pieceIndex = pieceIndex;
			this.sightingId = sightingId;
			this.equipmentParts = equipmentParts;
			this.itemId = itemId;
		}

		boolean containsPart(int index) {
			return pieceIndex == index || equipmentParts.contains(index);
		}
	}

	private final Callbacks callbacks;
	private final ViewportPanel viewport;


	private final DefaultListModel<String> rowListModel = new DefaultListModel<>();
	private final JList<String> rowList;
	private final List<Row> rows = new ArrayList<>();
	private final JTextField browserSearch = WorkspaceStyle.search("Filter nearby by name or ID");
	private final JLabel clientStatus = new JLabel("Waiting for scene");
	private final JLabel status = new JLabel("Select a nearby entity.");
	private final DefaultListModel<String> modelListModel = new DefaultListModel<>();
	private final java.util.BitSet hiddenModelParts = new java.util.BitSet();
	private final JList<String> modelList = new JList<>(modelListModel) {
		@Override
		protected void processMouseEvent(java.awt.event.MouseEvent event) {
			int index = locationToIndex(event.getPoint());
			var bounds = index < 0 ? null : getCellBounds(index, index);
			if (index > 0 && bounds != null && bounds.contains(event.getPoint())
				&& event.getX() >= bounds.x + bounds.width - 28 && javax.swing.SwingUtilities.isLeftMouseButton(event)) {
				if (event.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED)
					workspaceActions.edit("Model visibility", () -> toggleModelRow(index));
				return;
			}
			if (index > 0 && index < modelRows.size() && bounds != null && bounds.contains(event.getPoint())
				&& event.getX() < bounds.x + 26 && modelRows.get(index).equipmentParts.size() > 1
				&& javax.swing.SwingUtilities.isLeftMouseButton(event)) {
				if (event.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED) {
					int item = modelRows.get(index).itemId;
					if (!expandedItems.add(item))
						expandedItems.remove(item);
					rebuildModelRows();
				}
				return;
			}
			if (index > 0 && index == getSelectedIndex() && bounds != null && bounds.contains(event.getPoint())
				&& event.getID() == java.awt.event.MouseEvent.MOUSE_PRESSED && javax.swing.SwingUtilities.isLeftMouseButton(event)
				&& modelRows.get(index).equipmentParts.size() > 1 && expandedItems.add(modelRows.get(index).itemId))
				rebuildModelRows();
			super.processMouseEvent(event);
		}
	};
	private final List<Row> modelRows = new ArrayList<>();
	private final Set<Integer> expandedItems = new java.util.HashSet<>();
	private Row modelScope = new Row(-1);
	private int hoveredModelRow = -1;
	private int[] modelLightCounts = new int[0];

	private final CardLayout workspaceLayout = new CardLayout();
	private final JPanel workspaces = new JPanel(workspaceLayout);
	private final JComboBox<String> viewLayout = new JComboBox<>(new String[] { "Together", "Side by side", "Single" });
	private final JPanel viewContent = new JPanel(new BorderLayout());
	private final EditorToolRail toolRail = new EditorToolRail();
	private final java.util.Map<String, List<List<javax.swing.AbstractButton>>> workspaceTools = new java.util.HashMap<>();
	private final List<ViewportPanel> splitViews = new ArrayList<>();

	private boolean rebuildingModels;
	private boolean gridVisible = true;
	private boolean solidVisible = true;


	private static final String[] CATEGORY_LABELS = { "Player", "World objects", "NPCs", "Graphics" };
	private final JToggleButton[] categories = new JToggleButton[CATEGORY_LABELS.length];
	private final JTextField definitionSearch = new JTextField();
	private final JTextField idField = new JTextField(7);
	private final JPanel loadPanel = new JPanel(new BorderLayout(4, 0));

	private final JButton placementNavBtn = new JButton("Placement");
	private final JButton definitionsNavBtn = new JButton("Definitions");
	private final JButton particlesNavBtn = new JButton("Particles");
	private final JToggleButton vertexPickButton = new JToggleButton("Vert");
	private final ParticlePropertiesPanel particlesPanel;
	private boolean editingParticles;
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel cards = new JPanel(cardLayout);

	private final JComboBox<String> lightBrushCombo = EditorSelect.<String>builder().build();
	private final JLabel editorHint = new JLabel();
	private final Set<Integer> groupVertices = new java.util.HashSet<>();
	private final Set<Integer> groupFaces = new java.util.HashSet<>();
	private final JLabel groupCount = new JLabel();
	private final JLabel groupScope = new JLabel();
	private final JButton groupAction = new JButton();
	private JPanel groupBar;
	private final JTextField pieceNameField = new JTextField();
	private final JTextField itemIdsField = new JTextField();
	private final JSpinner offsetXSpinner = new JSpinner(new SpinnerNumberModel(0.0, -256.0, 256.0, 0.5));
	private final JSpinner offsetYSpinner = new JSpinner(new SpinnerNumberModel(0.0, -256.0, 256.0, 0.5));
	private final JSpinner offsetZSpinner = new JSpinner(new SpinnerNumberModel(0.0, -256.0, 256.0, 0.5));

	private final JPanel anchorPanel = new JPanel();
	private final JLabel anchorTitle = DevUi.mutedLabel("No anchor selected");
	private final JComboBox<String> anchorLightCombo = new JComboBox<>();



	private final JLabel emptyLightLabel = new JLabel("Click a light in the viewport.");

	private final DefaultListModel<String> definitionListModel = new DefaultListModel<>();
	private final JList<String> definitionList = new JList<>(definitionListModel);
	private final JTextField defDescriptionField = new JTextField();
	private final JSpinner radiusSpinner = new JSpinner(new SpinnerNumberModel(300, 1, 5000, 10));
	private final JSpinner strengthSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.0, 100.0, 0.5));
	private final JButton colorButton = new JButton("Color");
	private final JComboBox<LightType> typeCombo = new JComboBox<>(LightType.values());
	private final JComboBox<Alignment> alignmentCombo = new JComboBox<>(Alignment.values());
	private final JSpinner defOffsetXSpinner = new JSpinner(new SpinnerNumberModel(0.0, -256.0, 256.0, 0.5));
	private final JSpinner defOffsetYSpinner = new JSpinner(new SpinnerNumberModel(0.0, -256.0, 256.0, 0.5));
	private final JSpinner defOffsetZSpinner = new JSpinner(new SpinnerNumberModel(0.0, -256.0, 256.0, 0.5));
	private final JSpinner durationSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 60000.0, 100.0));
	private final JSpinner rangeSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 100.0, 0.5));
	private final JSpinner innerConeSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 180.0, 1.0));
	private final JSpinner outerConeSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 180.0, 1.0));
	private final JSpinner conePitchSpinner = new JSpinner(new SpinnerNumberModel(0.0, -180.0, 180.0, 1.0));
	private final JTextField maskField = new JTextField();
	private final JSpinner maskScaleSpinner = new JSpinner(new SpinnerNumberModel(1.0, 0.1, 10.0, 0.1));
	private final JSpinner heightSpinner = new JSpinner(new SpinnerNumberModel(0, -1000, 5000, 5));
	private final JSpinner fadeInSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 60000, 10));
	private final JSpinner fadeOutSpinner = new JSpinner(new SpinnerNumberModel(50, 0, 60000, 10));
	private final JSpinner spawnDelaySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 60000, 10));
	private final JSpinner despawnDelaySpinner = new JSpinner(new SpinnerNumberModel(0, 0, 60000, 10));
	private final JSpinner renderableIndexSpinner = new JSpinner(new SpinnerNumberModel(-1, -1, 10, 1));
	private final JCheckBox fixedDespawnCheck = styleCheck("Fixed despawn time");
	private final JCheckBox despawnWithParentCheck = styleCheck("Despawn with parent");
	private final JCheckBox visibleOtherPlanesCheck = styleCheck("Visible from other planes");
	private final JCheckBox ignoreActorHidingCheck = styleCheck("Ignore actor hiding");
	private final JCheckBox waitForAnimationCheck = styleCheck("Wait for animation");
	private final JTextField animationIdsField = new JTextField();
	private final JTextField npcIdsField = new JTextField();
	private final JTextField objectIdsField = new JTextField();
	private final JTextField projectileIdsField = new JTextField();
	private final JTextField graphicsIdsField = new JTextField();

	private final JSlider scrubSlider = new JSlider(0, 0, 0);
	private final JLabel scrubLabel = new JLabel(" ");
	private final JPanel scrubPanel = new JPanel(new BorderLayout(6, 0));

	@Nullable
	private float[][] recordingXs;
	@Nullable
	private float[][] recordingYs;
	@Nullable
	private float[][] recordingZs;
	@Nullable
	private int[] recordingFrames;

	private ModelSnapshot snapshot;
	@Nullable
	private String targetRootLabel;
	private int appliedPieceIndex = -1;
	private int selectedMode;
	private int selectedCaptureId = -1;
	private int lastAutoLoadedCaptureId = -1;
	private boolean populating;
	private WorkspaceActions workspaceActions;
	private final java.util.prefs.Preferences editorPreferences = java.util.prefs.Preferences.userNodeForPackage(LightViewerFrame.class);
	private double offsetSnap = editorPreferences.getDouble("offsetSnap", 0);
	private boolean rebuildingRows;
	@Nullable
	private AnchorSelection selectedAnchor;
	@Nullable
	private String selectedDefinitionId;

	public LightViewerFrame(Callbacks callbacks) {
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		this.callbacks = callbacks;

		viewport = new ViewportPanel(
			this::pickModel,
			(indices, add, target) -> {
				if (!editingParticles)
					selectGroup(indices, target);
			}
		);
		particlesPanel = new ParticlePropertiesPanel(viewport);
		viewport.setShowGrid(true);
		viewport.setOnHoverChanged(this::updateHoverHint);

		rowList = new JList<>(rowListModel);
		rowList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		rowList.setFixedCellHeight(24);
		DevUi.styleList(rowList);

		rowList.setCellRenderer(new DefaultListCellRenderer() {
			@Override
			public Component getListCellRendererComponent(
				JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus
			) {
				super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				setFont(WorkspaceStyle.UI);
				setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
				String[] letters = { "P", "O", "N", "G" };
				int[] colors = { 0xE8A33D, 0x7CB6A0, 0xD97A7A, 0xC79BE0 };
				setIcon(WorkspaceStyle.badge(letters[selectedMode], new Color(colors[selectedMode])));
				setIconTextGap(7);
				if (isSelected) {
					setBackground(new Color(51, 77, 115));
					setForeground(new Color(230, 230, 230));
				}
				return this;
			}
		});
		rowList.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting() || rebuildingRows)
				return;
			int index = rowList.getSelectedIndex();
			if (index < 0 || index >= rows.size())
				return;
			Row row = rows.get(index);
			if (row.sightingId >= 0)
				applyInSceneCaptureRow(row.sightingId);
		});

		modelList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		modelList.setFixedCellHeight(26);
		DevUi.styleList(modelList);

		modelList.setCellRenderer(new DefaultListCellRenderer() {
			private int modelIndex;
			private String meta = "";
			@Override protected void paintComponent(java.awt.Graphics graphics) {
				super.paintComponent(graphics);
				var g = graphics.create();
				if (modelIndex > 0 && modelIndex < modelRows.size() && snapshot != null) {
					Row row = modelRows.get(modelIndex);
					if (row.equipmentParts.isEmpty()) {
						int itemId = snapshot.getPieces().get(row.pieceIndex).getItemId();
						if (itemId >= 0) {
							boolean last = modelIndex + 1 == modelRows.size();
							if (!last) {
								Row next = modelRows.get(modelIndex + 1);
								last = !next.equipmentParts.isEmpty()
									|| snapshot.getPieces().get(next.pieceIndex).getItemId() != itemId;
							}
							g.setColor(WorkspaceStyle.BORDER);
							g.drawLine(24, 0, 24, last ? getHeight() / 2 + 8 : getHeight() - 1);
						}
					}
				}
				g.setFont(WorkspaceStyle.MONO.deriveFont(10f));
				g.setColor(WorkspaceStyle.DIM);
				g.drawString(meta, getWidth() - (modelIndex > 0 ? 32 : 8) - g.getFontMetrics().stringWidth(meta), 17);
				g.dispose();
				if (modelIndex > 0 && modelIndex < modelRows.size())
					WorkspaceStyle.icon(modelRowHidden(modelRows.get(modelIndex)) ? "eye-off" : "eye", 14)
						.paintIcon(this, graphics, getWidth() - 22, (getHeight() - 14) / 2);
			}
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
				super.getListCellRendererComponent(list, value, index, selected, focus);
				int[] tints = { 0x8FA8C8, 0xD08A5A, 0xE0B15A, 0x7CB6A0, 0x5F9E8A, 0xA99BD0 };
				if (index >= 0 && index < modelRows.size()) {
					Row row = modelRows.get(index);
					if (index == 0)
						setIcon(WorkspaceStyle.badge("", new Color(0xE8A33D)));
					else if (row.equipmentParts.size() > 1)
						setIcon(WorkspaceStyle.icon(expandedItems.contains(row.itemId) ? "chevron-down" : "chevron-right", 10));
					else if (row.equipmentParts.isEmpty())
						setIcon(WorkspaceStyle.badge("", new Color(tints[row.pieceIndex % tints.length])));
					else
						setIcon(null);
				}
				setFont(index > 0 && modelRows.get(index).equipmentParts.isEmpty() ? WorkspaceStyle.MONO.deriveFont(12f) : WorkspaceStyle.UI);
				setIconTextGap(8);
				modelIndex = index;
				meta = index >= 0 && index < modelRows.size() ? modelRowMeta(modelRows.get(index)) : "";
				int reserved = getFontMetrics(WorkspaceStyle.MONO.deriveFont(10f)).stringWidth(meta) + (index > 0 ? 40 : 16);
				setBorder(BorderFactory.createEmptyBorder(0, index > 0 && modelRows.get(index).equipmentParts.isEmpty() ? 32 : 8, 0, reserved));
				if (!selected && index > 0 && modelScope.itemId >= 0 && modelRows.get(index).equipmentParts.isEmpty()
					&& modelScope.containsPart(modelRows.get(index).pieceIndex)) {
					boolean shownInSingle = viewLayout.getSelectedIndex() == 2 && modelRows.get(index).pieceIndex == appliedPieceIndex;
					setBackground(new Color(shownInSingle ? 0x334D73 : 0x2B3A52));
				}
				if (!selected && index == hoveredModelRow)
					setBackground(getBackground().brighter());
				if (selected) {
					setBackground(new Color(51, 77, 115));
					setForeground(new Color(230, 230, 230));
				}
				if (index > 0 && modelRowHidden(modelRows.get(index)))
					setForeground(new Color(0x6A6A6A));
				return this;
			}
		});
		modelList.registerKeyboardAction(e -> workspaceActions.edit("Model visibility", () -> toggleModelRow(modelList.getSelectedIndex())),
			javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_SPACE, 0), JComponent.WHEN_FOCUSED);
		var modelHover = new java.awt.event.MouseAdapter() {
			@Override public void mouseMoved(java.awt.event.MouseEvent e) {
				int index = modelList.locationToIndex(e.getPoint());
				var bounds = index < 0 ? null : modelList.getCellBounds(index, index);
				setHoveredRow(bounds != null && bounds.contains(e.getPoint()) ? index : -1);
			}

			@Override public void mouseExited(java.awt.event.MouseEvent e) {
				setHoveredRow(-1);
			}

			private void setHoveredRow(int index) {
				if (index == hoveredModelRow)
					return;
				hoveredModelRow = index;
				modelList.repaint();
			}
		};
		modelList.addMouseListener(modelHover);
		modelList.addMouseMotionListener(modelHover);
		modelList.addListSelectionListener(e -> {
			int index = modelList.getSelectedIndex();
			if (!e.getValueIsAdjusting() && !rebuildingModels && index >= 0 && index < modelRows.size())
				selectModelScope(modelRows.get(index));
		});
		buildUi();
		applyWorkspaceFonts(getContentPane());
		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosed(WindowEvent e) {
				workspaceActions.stop();
				particlesPanel.setActive(false);
				releaseSnapshot();
			}
		});
	}

	private void pickModel(ViewportPanel.Pick pick, ViewportPanel.PickAction action) {
		if (editingParticles)
			particlesPanel.pick(snapshot, appliedPieceIndex, pick, action);
		else
			workspaceActions.edit(action + " light", () -> callbacks.pickClicked(pick, action));
	}

	private static ImageIcon icon(String name) {
		if (name.startsWith("handoff-"))
			return WorkspaceStyle.icon(name.substring(8));
		var url = WorkspaceStyle.class.getResource("icons/" + name + ".png");
		return url == null ? new ImageIcon() : new ImageIcon(url);
	}

	private void buildUi() {
		workspaceActions = new WorkspaceActions(this, this::captureWorkspace, this::restoreWorkspace, status);
		workspaceActions.addCommandSource(() -> {
			var commands = new ArrayList<WorkspaceActions.Command>();
			for (String name : callbacks.getLightDescriptions()) {
				commands.add(new WorkspaceActions.Command("Definition", "Go to " + name, "", () -> {
					showCard("definitions");
					refreshDefinitionList(name);
				}, () -> definitionColor(name)));
			}
			return commands;
		});
		workspaceActions.addCommandSource(this::lightCommands);
		var refresh = EditorButton.builder()
			.icon("refresh", 16)
			.tooltip("Recapture the target (~3s animation recording for players)")
			.size(26, 48)
			.onClick(callbacks::refreshSightings)
			.build();
		var saveModels = EditorButton.builder()
			.text("Save placement")
			.tooltip("Write model_lights.json")
			.onClick(callbacks::saveModelLights)
			.build();
		var saveDefs = EditorButton.builder()
			.style(WorkspaceStyle.Button.PRIMARY)
			.text("Save lights")
			.icon("save", 13)
			.tooltip("Write lights.json")
			.onClick(callbacks::saveLightDefinitions)
			.build();

		JButton loadBtn = new JButton("Load");
		loadBtn.addActionListener(e -> loadFromIdField());
		DevUi.sizeField(idField);
		loadPanel.setOpaque(false);
		loadPanel.add(new JLabel("ID "), BorderLayout.WEST);
		loadPanel.add(idField, BorderLayout.CENTER);
		loadPanel.add(loadBtn, BorderLayout.EAST);

		var browserButton = EditorButton.builder().style(WorkspaceStyle.Button.TAB)
			.text("Browser").icon("panel-browser", 16).buildToggle(true);


		placementNavBtn.setText("Lights");
		placementNavBtn.setIcon(null);
		definitionsNavBtn.setIcon(null);
		particlesNavBtn.setIcon(null);
		JButton uvTab = EditorButton.builder().style(WorkspaceStyle.Button.TAB)
			.text("UV Editing").enabled(false).build();

		placementNavBtn.addActionListener(e -> showCard("placement"));
		definitionsNavBtn.addActionListener(e -> showCard("definitions"));
		particlesNavBtn.addActionListener(e -> showCard("particles"));
		JButton[] tabs = {placementNavBtn, uvTab, particlesNavBtn, definitionsNavBtn};
		for (JButton tab : tabs)
			WorkspaceStyle.button(tab, WorkspaceStyle.Button.TAB);
		WorkspaceStyle.soon(uvTab);
		var soonBadge = uvTab.getIcon();
		var saves = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 4, 0));
		saves.setOpaque(false);



		saves.add(saveModels);
		saves.add(saveDefs);
		var top = editorToolbar(browserButton, workspaceActions.toolbar(), saves, compact -> {
			browserButton.setText(compact ? null : "Browser");
			uvTab.setDisabledIcon(compact ? null : soonBadge);
			uvTab.setIcon(compact ? null : soonBadge);
			saveModels.setText(compact ? "Placement" : "Save placement");
		}, tabs);
		workspaceActions.register("File", "Save placement", "", null, callbacks::saveModelLights);
		workspaceActions.register("File", "Save lights", "", null, callbacks::saveLightDefinitions);
		workspaceActions.register("Window", "Toggle browser panel", "", null, browserButton::doClick);
		workspaceActions.register("Window", "Go to Lights", "", null, () -> showCard("placement"));
		workspaceActions.register("Window", "Go to Definitions", "", null, () -> showCard("definitions"));
		workspaceActions.register("Definition", "Duplicate definition", "Ctrl D", "ctrl D", this::duplicateSelectedDefinition);
		workspaceActions.register("Light", "Delete selected light", "Del", "DELETE", this::deleteSelectedLight);
		workspaceActions.register("Light", "Mirror across X", "M", "M", () -> mirrorSelected(false));
		workspaceActions.register("Light", "Mirror across Z", "Shift M", "shift M", () -> mirrorSelected(true));
		workspaceActions.register("View", "Layout: Together", "T", null, () -> viewLayout.setSelectedIndex(0));
		workspaceActions.register("View", "Layout: Side by side", "S", null, () -> viewLayout.setSelectedIndex(1));
		workspaceActions.register("View", "Layout: Single", "G", null, () -> viewLayout.setSelectedIndex(2));
		workspaceActions.register("View", "Frame all", "F", null, () -> eachView(ViewportPanel::resetView));
		workspaceActions.register("Tool", "Select", "Q", null, () -> viewport.setEditMode(ViewportPanel.EditMode.SELECT));
		workspaceActions.register("Tool", "Place light", "P", null, () -> viewport.setEditMode(ViewportPanel.EditMode.PLACE));
		workspaceActions.register("Tool", "Remove light", "X", null, () -> viewport.setEditMode(ViewportPanel.EditMode.DELETE));
		workspaceActions.register("Tool", "Vertex pick", "1", null, () -> viewport.setPickTarget(ViewportPanel.PickTarget.POINT));
		workspaceActions.register("Tool", "Face pick", "2", null, () -> viewport.setPickTarget(ViewportPanel.PickTarget.FACE));

		var browser = new JPanel(new BorderLayout());
		browser.setBackground(WorkspaceStyle.PANEL);
		var browserHeader = new JPanel();
		browserHeader.setLayout(new BoxLayout(browserHeader, BoxLayout.Y_AXIS));
		browserHeader.setBackground(WorkspaceStyle.PANEL);
		var browserTitle = new JPanel(new BorderLayout(8, 0));
		browserTitle.setBackground(WorkspaceStyle.HEADER);
		var browserGrip = panelHeader("Browser");
		browserTitle.add(browserGrip, BorderLayout.WEST);
		clientStatus.setFont(WorkspaceStyle.UI.deriveFont(11f));
		browserTitle.add(clientStatus, BorderLayout.CENTER);
		var closeBrowser = new JButton(WorkspaceStyle.icon("close", 12));
		closeBrowser.setToolTipText("Hide browser");
		WorkspaceStyle.button(closeBrowser, WorkspaceStyle.Button.TAB);
		closeBrowser.addActionListener(e -> browserButton.doClick());
		browserTitle.add(closeBrowser, BorderLayout.EAST);
		browserHeader.add(browserTitle);
		var browseControls = new JPanel(new BorderLayout(4, 0));
		browseControls.setOpaque(false);
		browseControls.setBorder(BorderFactory.createEmptyBorder(8, 8, 6, 8));
		var categoryGrid = new JPanel(new java.awt.GridLayout(2, 2, 3, 3));
		categoryGrid.setBackground(WorkspaceStyle.MENU);
		categoryGrid.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
		var categoryGroup = new javax.swing.ButtonGroup();
		for (int i = 0; i < categories.length; i++) {
			final int mode = i;
			var button = categories[i] = new JToggleButton(CATEGORY_LABELS[i]);
			WorkspaceStyle.button(button, WorkspaceStyle.Button.TAB);
			button.addActionListener(e -> selectCategory(mode));
			categoryGroup.add(button);
			categoryGrid.add(button);
		}
		categories[0].setSelected(true);
		refresh.setText(null);
		refresh.getAccessibleContext().setAccessibleName("Rescan nearby entities");
		refresh.setToolTipText("Rescan nearby entities");
		browseControls.add(categoryGrid, BorderLayout.CENTER);
		browseControls.add(refresh, BorderLayout.EAST);
		browserHeader.add(browseControls);
		browserSearch.setToolTipText("Filter nearby entities by name or ID");
		var search = new JPanel(new BorderLayout(6, 0));
		search.setBackground(WorkspaceStyle.INPUT);
		search.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createEmptyBorder(0, 8, 6, 8),
			BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(WorkspaceStyle.BORDER),
				BorderFactory.createEmptyBorder(0, 6, 0, 6))));
		search.add(new JLabel(WorkspaceStyle.icon("search", 12)), BorderLayout.WEST);
		browserSearch.setBorder(null);
		browserSearch.setBackground(WorkspaceStyle.INPUT);
		browserSearch.setPreferredSize(new Dimension(0, 22));
		search.add(browserSearch);
		browserHeader.add(search);
		browserSearch.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { rebuildRows(); }
			public void removeUpdate(DocumentEvent e) { rebuildRows(); }
			public void changedUpdate(DocumentEvent e) { rebuildRows(); }
		});
		browserHeader.add(loadPanel);
		updateLoadPanel();
		browser.add(browserHeader, BorderLayout.NORTH);
		browser.add(WorkspaceStyle.listScroll(rowList), BorderLayout.CENTER);
		browser.setMinimumSize(new Dimension(220, 70));

		var models = new JPanel(new BorderLayout());
		models.setBackground(WorkspaceStyle.PANEL);
		var modelsGrip = panelHeader("Models");
		models.add(modelsGrip, BorderLayout.NORTH);
		models.add(WorkspaceStyle.listScroll(modelList), BorderLayout.CENTER);

		cards.add(buildPlacementPanel(), "placement");
		cards.add(DevUi.scroll(particlesPanel), "particles");
		var properties = new JPanel(new BorderLayout());
		properties.setBackground(WorkspaceStyle.PANEL);
		var propertiesGrip = panelHeader("Properties   Light");
		properties.add(propertiesGrip, BorderLayout.NORTH);
		properties.add(cards, BorderLayout.CENTER);
		scrubSlider.addChangeListener(e -> applyScrub());
		scrubPanel.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		scrubPanel.add(scrubSlider, BorderLayout.CENTER);
		scrubPanel.add(scrubLabel, BorderLayout.EAST);
		scrubPanel.setVisible(false);

		JPanel canvas = new JPanel(new BorderLayout());
		DevUi.themePanel(canvas);
		var canvasHeader = new JPanel(new BorderLayout());
		DevUi.themePanel(canvasHeader);
		canvasHeader.add(buildViewportToolbar(), BorderLayout.CENTER);

		groupBar = buildGroupBar();
		var layers = new javax.swing.JLayeredPane() {
			@Override
			public void doLayout() {
				viewContent.setBounds(0, 0, getWidth(), getHeight());
				var size = canvasHeader.getPreferredSize();
				canvasHeader.setBounds(0, 0, Math.max(0, getWidth() - 85), size.height);
				Dimension groupSize = groupBar.getPreferredSize();
				int width = Math.min(groupSize.width, Math.max(0, getWidth() - 16));
				groupBar.setBounds((getWidth() - width) / 2, getHeight() - groupSize.height - 8, width, groupSize.height);
			}
		};
		canvasHeader.setOpaque(false);
		layers.add(viewContent, javax.swing.JLayeredPane.DEFAULT_LAYER);
		layers.add(canvasHeader, javax.swing.JLayeredPane.PALETTE_LAYER);
		layers.add(groupBar, javax.swing.JLayeredPane.PALETTE_LAYER);
		canvas.add(layers, BorderLayout.CENTER);
		canvas.add(scrubPanel, BorderLayout.SOUTH);
		canvas.setMinimumSize(new Dimension(335, 0));

		var docks = WorkspaceDocks.builder(canvas)
			.primaryPanel(browser, WorkspaceDocks.Side.LEFT)
			.panel(models, WorkspaceDocks.Side.RIGHT)
			.panel(properties, WorkspaceDocks.Side.RIGHT)
			.movable(browser, browserGrip, "Browser")
			.movable(browser, browserTitle, "Browser")
			.movable(browser, clientStatus, "Browser")
			.movable(models, modelsGrip, "Models")
			.movable(properties, propertiesGrip, "Properties")
			.build();
		browserButton.addActionListener(e -> docks.setPrimaryPanelVisible(browserButton.isSelected()));
		var sceneWorkspace = new JPanel(new BorderLayout(1, 0));
		sceneWorkspace.setBackground(new Color(20, 20, 20));
		sceneWorkspace.add(toolRail, BorderLayout.WEST);
		sceneWorkspace.add(docks, BorderLayout.CENTER);
		workspaces.add(sceneWorkspace, "scene");
		workspaces.add(buildDefinitionsPanel(), "definitions");
		JComponent body = workspaces;
		status.setOpaque(true);
		status.setBackground(new Color(35, 35, 35));
		status.setForeground(new Color(207, 207, 207));
		status.setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
		status.setPreferredSize(new Dimension(0, 24));
		var footer = new JPanel(new BorderLayout(16, 0));
		footer.setBackground(WorkspaceStyle.BAR);
		footer.add(status, BorderLayout.CENTER);
		workspaceActions.draftStatus.setForeground(WorkspaceStyle.DIM);
		workspaceActions.draftStatus.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 10));
		footer.add(workspaceActions.draftStatus, BorderLayout.EAST);
		
		workspaceActions.start();
		if (workspaceActions.hasDraft()) {
			var banner = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 10, 5));
			banner.setBackground(WorkspaceStyle.MENU);
			var message = new JLabel("Autosaved draft from " + workspaceActions.draftTime(), WorkspaceStyle.badge("", new Color(0xE8A33D)), javax.swing.SwingConstants.LEFT);
			banner.setBorder(BorderFactory.createLineBorder(WorkspaceStyle.BORDER));
			banner.add(message);
			var restore = new JButton("Restore");
			var dismiss = new JButton("Dismiss");
			WorkspaceStyle.button(restore, WorkspaceStyle.Button.PRIMARY);
			WorkspaceStyle.button(dismiss, WorkspaceStyle.Button.TAB);
			restore.addActionListener(e -> { workspaceActions.restoreDraft(); banner.setVisible(false); });
			dismiss.addActionListener(e -> banner.setVisible(false));
			banner.add(restore);
			banner.add(dismiss);
			var center = new javax.swing.JLayeredPane() {
				@Override public void doLayout() {
					workspaces.setBounds(0, 0, getWidth(), getHeight());
					var preferred = banner.getPreferredSize();
					banner.setBounds((getWidth() - preferred.width) / 2, 7, preferred.width, preferred.height);
				}
			};
			center.add(workspaces, javax.swing.JLayeredPane.DEFAULT_LAYER);
			center.add(banner, javax.swing.JLayeredPane.PALETTE_LAYER);
			body = center;
		}
		installEditorContent(top, body, footer);
		showCard("placement");

	}

	private static void applyWorkspaceFonts(Component component) {
		Font font = component.getFont();
		if (font != null && font != WorkspaceStyle.SEMIBOLD && font != WorkspaceStyle.MONO)
			component.setFont(component instanceof javax.swing.JSpinner.DefaultEditor || component instanceof javax.swing.JFormattedTextField
				? WorkspaceStyle.MONO : font.isBold() ? WorkspaceStyle.SEMIBOLD : WorkspaceStyle.UI.deriveFont(Math.min(12f, font.getSize2D())));
		if (component instanceof Container)
			for (Component child : ((Container) component).getComponents())
				applyWorkspaceFonts(child);
	}

	private static JLabel panelHeader(String text) {
		JLabel header = new JLabel(text) {
			@Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, 30); }
		};
		header.setFont(WorkspaceStyle.SEMIBOLD);
		header.setIcon(WorkspaceStyle.icon("grip", 12));
		header.setIconTextGap(8);
		header.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
		header.setOpaque(true);
		header.setBackground(new Color(44, 44, 44));
		return header;
	}

	private com.google.gson.JsonObject captureWorkspace() {
		var data = callbacks.captureEditorData();
		if (data == null)
			data = new com.google.gson.JsonObject();
		var visibility = new com.google.gson.JsonArray();
		hiddenModelParts.stream().forEach(visibility::add);
		data.add("hidden", visibility);
		data.addProperty("entity", targetRootLabel);
		return data;
	}

	private void restoreWorkspace(com.google.gson.JsonObject data) {
		callbacks.restoreEditorData(data);
		hiddenModelParts.clear();
		if (snapshot != null && data.has("hidden") && data.has("entity") && !data.get("entity").isJsonNull()
			&& java.util.Objects.equals(targetRootLabel, data.get("entity").getAsString())) {
			for (var part : data.getAsJsonArray("hidden")) {
				int index = part.getAsInt();
				if (index >= 0 && index < snapshot.getPieces().size())
					hiddenModelParts.set(index);
			}
		}
		selectAnchor(null);
		clearGroup();
		refreshDefinitionList(selectedDefinitionId);
		loadPieceSettings();
		rebuildViewportLayout();
	}

	private float snappedOffset(JSpinner spinner) {
		double value = ((Number) spinner.getValue()).doubleValue();
		return (float) (offsetSnap == 0 ? value : Math.max(-256, Math.min(256, Math.round(value / offsetSnap) * offsetSnap)));
	}

	private JPanel buildSnapControl() {
		var track = new JPanel(new java.awt.GridLayout(1, 5, 1, 0));
		track.setBackground(WorkspaceStyle.MENU);
		var options = new javax.swing.ButtonGroup();
		String[] labels = { "Off", ".01", ".05", ".1", ".25" };
		double[] values = { 0, .01, .05, .1, .25 };
		for (int i = 0; i < values.length; i++) {
			double value = values[i];
			var segment = new JToggleButton(labels[i]);
			WorkspaceStyle.button(segment, WorkspaceStyle.Button.SEGMENT);
			segment.setMargin(new java.awt.Insets(0, 0, 0, 0));
			segment.setSelected(offsetSnap == value);
			segment.setToolTipText("Offset steps and drags snap to this increment");
			segment.addActionListener(e -> {
				offsetSnap = value;
				editorPreferences.putDouble("offsetSnap", value);
				populating = true;
				for (JSpinner spinner : new JSpinner[] { offsetXSpinner, offsetYSpinner, offsetZSpinner })
					((SpinnerNumberModel) spinner.getModel()).setStepSize(value == 0 ? .01 : value);
				populating = false;
			});
			options.add(segment);
			track.add(segment);
			workspaceActions.register("Light", "Offset snap: " + labels[i], "", null, segment::doClick);
		}
		for (JSpinner spinner : new JSpinner[] { offsetXSpinner, offsetYSpinner, offsetZSpinner })
			((SpinnerNumberModel) spinner.getModel()).setStepSize(offsetSnap == 0 ? .01 : offsetSnap);
		return track;
	}

	private void duplicateSelectedDefinition() {
		String source = definitionsNavBtn.isSelected() ? selectedDefinitionId
			: selectedAnchor == null ? null : callbacks.getAnchorLightDescription(selectedAnchor);
		if (source == null)
			return;
		var names = callbacks.getLightDescriptions();
		String name = source + "_COPY";
		for (int i = 2; names.contains(name); i++)
			name = source + "_COPY" + i;
		String duplicate = name;
		workspaceActions.edit("Duplicate " + source, () -> callbacks.duplicateDefinition(source, duplicate));
		showCard("definitions");
		refreshDefinitionList(duplicate);
		status.setText("Duplicated " + source + " → " + duplicate);
	}

	private Color definitionColor(String name) {
		LightDefinition definition = callbacks.getDefinition(name);
		if (definition == null || definition.color == null || definition.color.length != 3)
			return null;
		float[] rgb = ColorUtils.linearToSrgb(definition.color);
		return new Color(clampColor(rgb[0]), clampColor(rgb[1]), clampColor(rgb[2]));
	}

	private static float clampColor(float channel) {
		return Math.max(0, Math.min(1, channel));
	}

	private List<WorkspaceActions.Command> lightCommands() {
		var commands = new ArrayList<WorkspaceActions.Command>();
		if (snapshot == null)
			return commands;
		for (int i = 0; i < snapshot.getPieces().size(); i++) {
			var piece = snapshot.getPieces().get(i);
			for (int index : callbacks.litVertices(i))
				addLightCommand(commands, new AnchorSelection(i, piece.localIndexOf(index), false), piece.getLabel());
			for (int index : callbacks.litFaces(i))
				addLightCommand(commands, new AnchorSelection(i, piece.localFaceIndex(index), true), piece.getLabel());
		}
		return commands;
	}

	private void addLightCommand(List<WorkspaceActions.Command> commands, AnchorSelection anchor, String pieceName) {
		if (anchor.localIndex < 0)
			return;
		String name = callbacks.getAnchorLightDescription(anchor);
		if (name == null)
			return;
		String label = "Select " + name + " · " + pieceName + (anchor.triangle ? " face " : " vert ") + anchor.localIndex;
		commands.add(new WorkspaceActions.Command("Light", label, "", () -> {
			showCard("placement");
			hiddenModelParts.clear(anchor.pieceIndex);
			selectModelScope(new Row(anchor.pieceIndex));
			selectAnchor(anchor);
		}, () -> definitionColor(name)));
	}

	private void deleteSelectedLight() {
		if (snapshot == null || selectedAnchor == null || definitionsNavBtn.isSelected() || editingParticles)
			return;
		var anchor = selectedAnchor;
		var piece = snapshot.getPieces().get(anchor.pieceIndex);
		applyPiece(anchor.pieceIndex);
		var target = anchor.triangle ? ViewportPanel.PickTarget.FACE : ViewportPanel.PickTarget.POINT;
		int index = anchor.triangle ? piece.getFaces()[anchor.localIndex] : piece.getVertices()[anchor.localIndex];
		workspaceActions.edit("Remove light", () -> callbacks.boxSelected(Set.of(index), false, target));
		selectAnchor(null);
	}

	private void mirrorSelected(boolean zAxis) {
		if (selectedAnchor == null || definitionsNavBtn.isSelected() || editingParticles)
			return;
		var anchor = selectedAnchor;
		workspaceActions.edit("Mirror light across " + (zAxis ? "Z" : "X"), () -> callbacks.mirrorAnchor(anchor, zAxis));
	}

	private JComponent buildBrushSection() {
		var brush = new JPanel(new BorderLayout());
		brush.setBackground(WorkspaceStyle.PANEL);
		brush.setAlignmentX(Component.LEFT_ALIGNMENT);
		brush.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));

		var header = EditorText.builder("Brush").font(WorkspaceStyle.SEMIBOLD).color(new Color(0xDDDDDD)).build();
		header.setOpaque(true);
		header.setBackground(WorkspaceStyle.HEADER);
		header.setPreferredSize(new Dimension(0, 28));
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, WorkspaceStyle.MENU),
			BorderFactory.createEmptyBorder(0, 10, 0, 10)));
		brush.add(header, BorderLayout.NORTH);

		var body = new JPanel(new net.miginfocom.swing.MigLayout(
			"insets 10px, gap 8px 0px, fillx", "[78px!][grow,fill]", "[24px!]"));
		body.setOpaque(false);
		var label = new JLabel("Definition");
		label.setLabelFor(lightBrushCombo);
		label.setFont(WorkspaceStyle.UI);
		label.setForeground(new Color(0x9A9A9A));
		body.add(label);
		body.add(lightBrushCombo);
		brush.add(body, BorderLayout.CENTER);
		return brush;
	}

	private void styleDefinitionSelector(JComboBox<String> selector) {
		EditorSelect.style(selector);
		selector.setRenderer(new DefaultListCellRenderer() {
			@Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
				super.getListCellRendererComponent(list, value, index, selected, focus);
				setFont(WorkspaceStyle.MONO);
				setIconTextGap(6);
				setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 3));
				setForeground(WorkspaceStyle.TEXT);
				setBackground(selected ? new Color(0x334D73) : WorkspaceStyle.MENU);
				setOpaque(index >= 0);
				Color color = value instanceof String ? definitionColor((String) value) : null;
				setIcon(color == null ? null : brushSwatch(color));
				return this;
			}
		});
	}

	private static javax.swing.Icon brushSwatch(Color color) {
		return new javax.swing.Icon() {
			public int getIconWidth() { return 16; }
			public int getIconHeight() { return 18; }

			public void paintIcon(Component component, java.awt.Graphics graphics, int x, int y) {
				var g = (java.awt.Graphics2D) graphics.create();
				g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				g.setPaint(new java.awt.RadialGradientPaint(x + 8f, y + 9f, 8f, new float[] {0f, 1f},
					new Color[] {new Color(color.getRed(), color.getGreen(), color.getBlue(), 150),
						new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)}));
				g.fillOval(x, y + 1, 16, 16);
				g.setColor(color);
				g.fillOval(x + 3, y + 4, 10, 10);
				g.dispose();
			}
		};
	}

	private JComponent buildPlacementPanel() {
		styleDefinitionSelector(lightBrushCombo);
		editorHint.addPropertyChangeListener("text", e -> lightBrushCombo.setToolTipText(editorHint.getText()));
		lightBrushCombo.addActionListener(e -> {
			if (populating)
				return;
			String desc = (String) lightBrushCombo.getSelectedItem();
			if (desc != null && !desc.isEmpty()) {
				callbacks.onLightBrushChanged(desc);
				viewport.setEditMode(ViewportPanel.EditMode.PLACE);
			}
			updateGroupBar();
		});

		DevUi.sizeField(pieceNameField);
		DevUi.sizeField(itemIdsField);
		pieceNameField.addActionListener(e -> applyPieceSettingsFromUi());
		itemIdsField.addActionListener(e -> applyPieceSettingsFromUi());

		for (JSpinner spinner : new JSpinner[] { offsetXSpinner, offsetYSpinner, offsetZSpinner })
			DevUi.sizeField(spinner);
		Runnable saveOffset = () -> {
			int pieceIndex = propertyPieceIndex();
			if (populating || pieceIndex < 0)
				return;
			float x = snappedOffset(offsetXSpinner);
			float y = snappedOffset(offsetYSpinner);
			float z = snappedOffset(offsetZSpinner);
			if (offsetSnap > 0) {
				populating = true;
				offsetXSpinner.setValue((double) x);
				offsetYSpinner.setValue((double) y);
				offsetZSpinner.setValue((double) z);
				populating = false;
			}
			workspaceActions.edit("Move model " + pieceIndex, () -> callbacks.applyProfileOffset(pieceIndex, x, y, z));
			updateActiveLightMarker();
		};
		offsetXSpinner.addChangeListener(e -> saveOffset.run());
		offsetYSpinner.addChangeListener(e -> saveOffset.run());
		offsetZSpinner.addChangeListener(e -> saveOffset.run());

		anchorPanel.setLayout(new BoxLayout(anchorPanel, BoxLayout.Y_AXIS));
		anchorPanel.setOpaque(false);
		anchorTitle.setFont(WorkspaceStyle.MONO);
		anchorTitle.setForeground(new Color(0xBDBDBD));
		styleDefinitionSelector(anchorLightCombo);
		anchorLightCombo.addActionListener(e -> {
			if (populating || selectedAnchor == null)
				return;
			String desc = (String) anchorLightCombo.getSelectedItem();
			if (desc != null)
				workspaceActions.edit("Change light definition", () -> callbacks.setAnchorLightDescription(selectedAnchor, desc));
			updateActiveLightMarker();
		});
		anchorPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		anchorPanel.add(propertyRow("Name", pieceNameField));
		anchorPanel.add(propertyRow("Anchor", anchorTitle));
		var definitionRow = new JPanel(new BorderLayout(4, 0));
		definitionRow.setOpaque(false);
		definitionRow.add(anchorLightCombo);
		var edit = new JButton("Edit", WorkspaceStyle.icon("open-external", 11));
		edit.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);
		WorkspaceStyle.button(edit, WorkspaceStyle.Button.SECONDARY);
		edit.addActionListener(e -> {
			String name = (String) anchorLightCombo.getSelectedItem();
			if (name != null) {
				showCard("definitions");
				refreshDefinitionList(name);
			}
		});
		definitionRow.add(edit, BorderLayout.EAST);
		anchorPanel.add(propertyRow("Definition", definitionRow));
		anchorPanel.add(propertyRow("Item IDs", itemIdsField));
		var offsets = new JPanel(new java.awt.GridLayout(3, 1, 0, 2));
		offsets.setOpaque(false);
		offsets.add(offsetRow("X", new Color(0xE05555), offsetXSpinner));
		offsets.add(offsetRow("Y", new Color(0x6CB348), offsetYSpinner));
		offsets.add(offsetRow("Z", new Color(0x4F86E0), offsetZSpinner));
		anchorPanel.add(propertyRow("Offset", offsets));
		anchorPanel.add(propertyRow("Snap", buildSnapControl()));
		var mirror = new JPanel(new java.awt.GridLayout(1, 2, 4, 0));
		for (boolean z : new boolean[] { false, true }) {
			var button = new JButton(z ? "Across Z" : "Across X", WorkspaceStyle.icon("mirror", 13));
			WorkspaceStyle.button(button, WorkspaceStyle.Button.SECONDARY);
			button.addActionListener(e -> mirrorSelected(z));
			mirror.add(button);
		}
		anchorPanel.add(propertyRow("Mirror", mirror));
		var actions = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
		actions.setOpaque(false);
		var reset = new JButton("Reset offset");
		WorkspaceStyle.button(reset, WorkspaceStyle.Button.SECONDARY);
		reset.addActionListener(e -> {
			int pieceIndex = propertyPieceIndex();
			if (pieceIndex < 0)
				return;
			workspaceActions.edit("Reset offset", () -> callbacks.applyProfileOffset(pieceIndex, 0, 0, 0));
			updateActiveLightMarker();
			loadPieceSettings();
		});
		var delete = EditorButton.builder().style(WorkspaceStyle.Button.DANGER)
			.text("Delete").icon("trash", 12).onClick(this::deleteSelectedLight).build();
		actions.add(reset);
		actions.add(delete);
		anchorPanel.add(propertyRow("", actions));
		anchorPanel.setVisible(false);

		class PropertyBody extends JPanel implements javax.swing.Scrollable {
			public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
			public int getScrollableUnitIncrement(java.awt.Rectangle visible, int orientation, int direction) { return 16; }
			public int getScrollableBlockIncrement(java.awt.Rectangle visible, int orientation, int direction) {
				return Math.max(16, visible.height - 16);
			}
			public boolean getScrollableTracksViewportWidth() { return true; }
			public boolean getScrollableTracksViewportHeight() { return false; }
		}
		JPanel editorBody = new PropertyBody();
		editorBody.setLayout(new BoxLayout(editorBody, BoxLayout.Y_AXIS));
		editorBody.setOpaque(false);
		editorBody.add(buildBrushSection());
		var selectedHeader = new JLabel("Selected light");
		selectedHeader.setOpaque(true);
		selectedHeader.setBackground(WorkspaceStyle.HEADER);
		selectedHeader.setForeground(new Color(0xDDDDDD));
		selectedHeader.setFont(WorkspaceStyle.SEMIBOLD);
		selectedHeader.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
		selectedHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
		selectedHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		selectedHeader.setPreferredSize(new Dimension(0, 28));
		editorBody.add(selectedHeader);
		emptyLightLabel.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
		emptyLightLabel.setForeground(WorkspaceStyle.DIM);
		emptyLightLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		editorBody.add(emptyLightLabel);
		anchorPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		editorBody.add(anchorPanel);

		var scroll = new JScrollPane(editorBody, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
			javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.getViewport().setBackground(WorkspaceStyle.PANEL);
		return scroll;
	}

	private JComponent propertyRow(String name, JComponent field) {
		var row = new JPanel(new net.miginfocom.swing.MigLayout("insets 0px 0px 6px 0px, gap 8px 0px, fillx",
			"[78px!][0px,grow,fill]", "[grow,center]"));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		var label = new JLabel(name);
		label.setFont(WorkspaceStyle.UI);
		label.setForeground(new Color(0x9A9A9A));
		row.add(label);
		row.add(field);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
		return row;
	}

	private JPanel offsetRow(String axis, Color color, JSpinner spinner) {
		spinner.setUI(new javax.swing.plaf.basic.BasicSpinnerUI() {
			@Override protected Component createNextButton() { return null; }
			@Override protected Component createPreviousButton() { return null; }
		});
		spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.000"));
		var value = ((JSpinner.DefaultEditor) spinner.getEditor()).getTextField();
		value.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
		value.setFont(WorkspaceStyle.MONO);
		value.setBorder(BorderFactory.createEmptyBorder());
		spinner.setBorder(BorderFactory.createEmptyBorder());
		spinner.setOpaque(false);
		value.setOpaque(false);
		var row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(WorkspaceStyle.INPUT);
		row.setBorder(new javax.swing.border.LineBorder(WorkspaceStyle.BORDER, 1, true));
		row.setPreferredSize(new Dimension(100, 24));
		var left = new JPanel(new BorderLayout(4, 0));
		left.setOpaque(false);
		var label = new JLabel(axis);
		label.setForeground(color);
		label.setFont(WorkspaceStyle.MONO);
		left.add(offsetArrow(spinner, false), BorderLayout.WEST);
		left.add(label);
		installOffsetScrub(spinner, label, value);
		row.add(left, BorderLayout.WEST);
		row.add(spinner);
		row.add(offsetArrow(spinner, true), BorderLayout.EAST);
		return row;
	}

	private void installOffsetScrub(JSpinner spinner, JComponent... handles) {
		var scrub = new java.awt.event.MouseAdapter() {
			private int startX;
			private double startValue;
			private boolean pressed;
			private boolean dragging;

			@Override public void mousePressed(java.awt.event.MouseEvent event) {
				pressed = spinner.isEnabled() && javax.swing.SwingUtilities.isLeftMouseButton(event);
				dragging = false;
				if (!pressed)
					return;
				startX = event.getXOnScreen();
				startValue = ((Number) spinner.getValue()).doubleValue();
			}

			@Override public void mouseDragged(java.awt.event.MouseEvent event) {
				if (!pressed || !spinner.isEnabled())
					return;
				int distance = event.getXOnScreen() - startX;
				if (!dragging && Math.abs(distance) < 3)
					return;
				dragging = true;
				var model = (SpinnerNumberModel) spinner.getModel();
				double value = startValue + distance * 0.002;
				value = offsetSnap > 0 ? Math.round(value / offsetSnap) * offsetSnap : Math.round(value * 1000) / 1000.0;
				double min = ((Number) model.getMinimum()).doubleValue();
				double max = ((Number) model.getMaximum()).doubleValue();
				model.setValue(Math.max(min, Math.min(max, value)));
				event.consume();
			}

			@Override public void mouseReleased(java.awt.event.MouseEvent event) {
				pressed = false;
				dragging = false;
			}
		};
		for (JComponent handle : handles) {
			handle.setCursor(WorkspaceStyle.cursor("scrub"));
			handle.addMouseListener(scrub);
			handle.addMouseMotionListener(scrub);
		}
	}

	private JButton offsetArrow(JSpinner spinner, boolean next) {
		var button = new JButton(WorkspaceStyle.icon(next ? "chevron-right" : "chevron-left", 9));
		WorkspaceStyle.button(button, WorkspaceStyle.Button.TAB);
		button.setPreferredSize(new Dimension(18, 22));
		button.addActionListener(e -> {
			Object value = next ? spinner.getNextValue() : spinner.getPreviousValue();
			if (value != null)
				spinner.setValue(value);
		});
		return button;
	}

	private JPanel buildDefinitionsPanel() {
		definitionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		DevUi.styleList(definitionList);
		definitionList.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting())
				return;
			selectedDefinitionId = definitionList.getSelectedValue();
			refreshDefinitionEditor();
		});

		JButton addDef = new JButton("New", WorkspaceStyle.icon("plus", 10));
		addDef.addActionListener(e -> {
			String name = javax.swing.JOptionPane.showInputDialog(this, "Light description:");
			if (name != null && !name.trim().isEmpty()) {
				workspaceActions.edit("Add " + name.trim(), () -> callbacks.createDefinition(name.trim()));
				refreshDefinitionList(name.trim());
			}
		});
		JButton delDef = new JButton("Delete", WorkspaceStyle.icon("trash", 12));
		delDef.addActionListener(e -> {
			if (selectedDefinitionId != null) {
				workspaceActions.edit("Delete " + selectedDefinitionId, () -> callbacks.deleteDefinition(selectedDefinitionId));
				refreshDefinitionList(null);
			}
		});
		WorkspaceStyle.button(addDef, WorkspaceStyle.Button.SECONDARY);
		WorkspaceStyle.button(delDef, WorkspaceStyle.Button.SECONDARY);

		JPanel defButtons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 4, 0));
		defButtons.setOpaque(false);
		var duplicate = new JButton("Duplicate", WorkspaceStyle.icon("duplicate", 11));
		WorkspaceStyle.button(duplicate, WorkspaceStyle.Button.TAB);
		duplicate.addActionListener(e -> duplicateSelectedDefinition());
		defButtons.add(duplicate);
		defButtons.add(addDef);
		defButtons.add(delDef);

		DevUi.sizeField(defDescriptionField);
		DevUi.sizeField(colorButton);
		DevUi.styleCombo(typeCombo);
		DevUi.styleCombo(alignmentCombo);
		for (JSpinner spinner : new JSpinner[] {
			radiusSpinner, strengthSpinner, heightSpinner, defOffsetXSpinner, defOffsetYSpinner, defOffsetZSpinner,
			durationSpinner, rangeSpinner, fadeInSpinner, fadeOutSpinner, spawnDelaySpinner, despawnDelaySpinner,
			innerConeSpinner, outerConeSpinner, conePitchSpinner, maskScaleSpinner, renderableIndexSpinner
		})
			DevUi.sizeField(spinner);
		for (JTextField field : new JTextField[] {
			maskField, animationIdsField, npcIdsField, objectIdsField, projectileIdsField, graphicsIdsField
		})
			DevUi.sizeField(field);

		animationIdsField.setToolTipText("Comma-separated animation IDs (or gameval names in lights.json)");
		npcIdsField.setToolTipText("Comma-separated NPC IDs for scene attachment");
		objectIdsField.setToolTipText("Comma-separated object IDs for scene attachment");
		projectileIdsField.setToolTipText("Comma-separated projectile/spotanim IDs");
		graphicsIdsField.setToolTipText("Comma-separated graphics object IDs");
		fadeInSpinner.setToolTipText("Fade-in duration in milliseconds");
		fadeOutSpinner.setToolTipText("Fade-out duration in milliseconds");
		spawnDelaySpinner.setToolTipText("Delay before the light becomes visible (ms)");
		despawnDelaySpinner.setToolTipText("Delay after despawn before removal (ms)");
		renderableIndexSpinner.setToolTipText("Renderable slot on multi-part objects (-1 = any)");
		maskField.setToolTipText("Mask from textures/light_masks/ or \"circle\". 256x256, soft edges via alpha/grayscale.");

		colorButton.addActionListener(e -> pickDefinitionColor());
		Runnable saveDef = this::saveDefinitionFromUi;
		defDescriptionField.getDocument().addDocumentListener(docListener(saveDef));
		for (JSpinner spinner : new JSpinner[] {
			radiusSpinner, strengthSpinner, heightSpinner, defOffsetXSpinner, defOffsetYSpinner, defOffsetZSpinner,
			durationSpinner, rangeSpinner, fadeInSpinner, fadeOutSpinner, spawnDelaySpinner, despawnDelaySpinner,
			innerConeSpinner, outerConeSpinner, conePitchSpinner, maskScaleSpinner, renderableIndexSpinner
		})
			spinner.addChangeListener(e -> saveDef.run());
		for (JTextField field : new JTextField[] {
			maskField, animationIdsField, npcIdsField, objectIdsField, projectileIdsField, graphicsIdsField
		})
			field.getDocument().addDocumentListener(docListener(saveDef));
		for (JCheckBox check : new JCheckBox[] {
			fixedDespawnCheck, despawnWithParentCheck, visibleOtherPlanesCheck,
			ignoreActorHidingCheck, waitForAnimationCheck
		})
			check.addActionListener(e -> saveDef.run());
		typeCombo.addActionListener(e -> saveDef.run());
		alignmentCombo.addActionListener(e -> saveDef.run());

		JPanel editor = new JPanel();
		editor.setLayout(new BoxLayout(editor, BoxLayout.Y_AXIS));
		editor.setOpaque(false);
		editor.add(DevUi.section("Identity", true, null,
			DevUi.formRow("Description", defDescriptionField)).root);
		editor.add(DevUi.section("Appearance", true, null,
			DevUi.formRow("Radius", radiusSpinner),
			DevUi.formRow("Strength", strengthSpinner),
			DevUi.formRow("Color", colorButton),
			DevUi.formRow("Type", typeCombo),
			DevUi.formRow("Height", heightSpinner)).root);
		editor.add(DevUi.section("Placement", true, null,
			DevUi.formRow("Alignment", alignmentCombo),
			DevUi.vectorRow("Offset", defOffsetXSpinner, defOffsetYSpinner, defOffsetZSpinner)).root);
		editor.add(DevUi.section("Animation", true, null,
			DevUi.formRow("Duration", durationSpinner),
			DevUi.formRow("Range", rangeSpinner),
			DevUi.formRow("Fade in (ms)", fadeInSpinner),
			DevUi.formRow("Fade out (ms)", fadeOutSpinner),
			DevUi.formRow("Spawn delay (ms)", spawnDelaySpinner),
			DevUi.formRow("Despawn delay (ms)", despawnDelaySpinner),
			DevUi.formRow("Fixed despawn", fixedDespawnCheck)).root);
		editor.add(DevUi.section("Spotlight", true, null,
			DevUi.formRow("Inner cone", innerConeSpinner),
			DevUi.formRow("Outer cone", outerConeSpinner),
			DevUi.formRow("Cone pitch", conePitchSpinner),
			DevUi.formRow("Mask", maskField),
			DevUi.formRow("Mask scale", maskScaleSpinner)).root);
		editor.add(DevUi.section("Behavior", true, null,
			DevUi.formRow("Despawn w/ parent", despawnWithParentCheck),
			DevUi.formRow("Other planes", visibleOtherPlanesCheck),
			DevUi.formRow("Ignore hiding", ignoreActorHidingCheck),
			DevUi.formRow("Wait for anim", waitForAnimationCheck),
			DevUi.formRow("Renderable #", renderableIndexSpinner)).root);
		editor.add(DevUi.section("Attachment filters", true, null,
			DevUi.formRow("Animation IDs", animationIdsField),
			DevUi.formRow("NPC IDs", npcIdsField),
			DevUi.formRow("Object IDs", objectIdsField),
			DevUi.formRow("Projectile IDs", projectileIdsField),
			DevUi.formRow("Graphics IDs", graphicsIdsField)).root);

		JScrollPane listScroll = new JScrollPane(definitionList);
		listScroll.setBorder(BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR));
		listScroll.getViewport().setBackground(ColorScheme.DARKER_GRAY_COLOR);
		listScroll.setPreferredSize(new Dimension(0, 140));

		JPanel panel = new JPanel(new BorderLayout(0, 6));
		DevUi.themePanel(panel);
		JPanel definitionBrowser = new JPanel(new BorderLayout());
		DevUi.themePanel(definitionBrowser);
		var definitionHeader = new JPanel();
		definitionHeader.setLayout(new BoxLayout(definitionHeader, BoxLayout.Y_AXIS));
		DevUi.themePanel(definitionHeader);
		definitionHeader.add(panelHeader("Light definitions"));
		definitionHeader.add(defButtons);
		definitionHeader.add(DevUi.formRow("Search", definitionSearch));
		definitionSearch.getDocument().addDocumentListener(new DocumentListener() {
			public void insertUpdate(DocumentEvent e) { refreshDefinitionList(selectedDefinitionId); }
			public void removeUpdate(DocumentEvent e) { refreshDefinitionList(selectedDefinitionId); }
			public void changedUpdate(DocumentEvent e) { refreshDefinitionList(selectedDefinitionId); }
		});
		definitionBrowser.add(definitionHeader, BorderLayout.NORTH);
		definitionBrowser.add(listScroll, BorderLayout.CENTER);
		definitionBrowser.setPreferredSize(new Dimension(280, 0));
		definitionBrowser.setBackground(WorkspaceStyle.PANEL);
		var definitionContent = new JPanel(new java.awt.GridBagLayout());
		definitionContent.setBackground(WorkspaceStyle.INPUT);
		var constraints = new java.awt.GridBagConstraints();
		constraints.anchor = java.awt.GridBagConstraints.NORTH;
		constraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
		constraints.weightx = 1;
		constraints.weighty = 1;
		constraints.insets = new java.awt.Insets(0, 28, 48, 28);
		definitionContent.add(editor, constraints);
		panel.setLayout(new BorderLayout(1, 0));
		panel.setBackground(WorkspaceStyle.GAP);
		panel.add(definitionBrowser, BorderLayout.WEST);
		panel.add(DevUi.scroll(definitionContent), BorderLayout.CENTER);
		return panel;
	}

	private JPanel buildViewportToolbar() {
		JToggleButton select = new JToggleButton();
		JToggleButton place = new JToggleButton();
		JToggleButton remove = new JToggleButton();
		JToggleButton face = new JToggleButton();
		JToggleButton vertex = vertexPickButton;
		JButton frameAll = new JButton();
		setToolIcon(select, "handoff-tool-select", "Select anchor (Q)");
		setToolIcon(place, "handoff-tool-place-light", "Place light (P)");
		setToolIcon(remove, "handoff-tool-remove-light", "Remove light (X)");
		setToolIcon(face, "handoff-pick-face", "Pick faces (2)");
		setToolIcon(vertex, "handoff-pick-vertex", "Pick vertices (1)");
		setToolIcon(frameAll, "handoff-frame-all", "Frame models (F)");
		var tools = new javax.swing.ButtonGroup();
		tools.add(select);
		tools.add(place);
		tools.add(remove);
		var targets = new javax.swing.ButtonGroup();
		targets.add(face);
		targets.add(vertex);
		place.setSelected(true);
		vertex.setSelected(true);
		viewport.setPickTarget(ViewportPanel.PickTarget.POINT);
		select.addActionListener(e -> viewport.setEditMode(ViewportPanel.EditMode.SELECT));
		place.addActionListener(e -> viewport.setEditMode(ViewportPanel.EditMode.PLACE));
		remove.addActionListener(e -> viewport.setEditMode(ViewportPanel.EditMode.DELETE));
		face.addActionListener(e -> viewport.setPickTarget(ViewportPanel.PickTarget.FACE));
		vertex.addActionListener(e -> viewport.setPickTarget(ViewportPanel.PickTarget.POINT));
		frameAll.addActionListener(e -> eachView(ViewportPanel::resetView));

		workspaceTools.put("placement", List.of(List.of(select, place, remove), List.of(vertex, face), List.of(frameAll)));
		workspaceTools.put("particles", List.of(List.of(select, place, remove), List.of(face), List.of(frameAll)));
		viewport.setOnEditModeChanged(mode -> {
			for (var view : splitViews)
				view.setEditMode(mode);
			select.setSelected(mode == ViewportPanel.EditMode.SELECT);
			place.setSelected(mode == ViewportPanel.EditMode.PLACE);
			remove.setSelected(mode == ViewportPanel.EditMode.DELETE);
			updateGroupBar();
			updateHint();
		});
		viewport.setOnPickTargetChanged(target -> {
			for (var view : splitViews)
				view.setPickTarget(target);
			face.setSelected(target == ViewportPanel.PickTarget.FACE);
			vertex.setSelected(target == ViewportPanel.PickTarget.POINT);
			updateHint();
		});

		String[] labels = { "Together", "Side by side", "Single" };
		String[] icons = { "handoff-layout-together", "handoff-layout-split", "handoff-layout-single" };
		JButton layout = new JButton(labels[0], icon(icons[0]));
		var layouts = new WorkspaceMenu(200);
		String[] shortcuts = { "T", "S", "G" };
		int[] layoutKeys = { java.awt.event.KeyEvent.VK_T, java.awt.event.KeyEvent.VK_S, java.awt.event.KeyEvent.VK_G };
		for (int i = 0; i < labels.length; i++) {
			final int index = i;
			layouts.addRow(labels[i], WorkspaceStyle.icon(icons[i].substring(8), 14), shortcuts[i],
				() -> viewLayout.getSelectedIndex() == index, e -> viewLayout.setSelectedIndex(index));
			layout.registerKeyboardAction(e -> {
				if (!(java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent))
					viewLayout.setSelectedIndex(index);
			}, javax.swing.KeyStroke.getKeyStroke(layoutKeys[i], 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
		}
		layout.addActionListener(e -> layouts.show(layout.getParent(), 8, layout.getY() + layout.getHeight() + 4));
		viewLayout.addActionListener(e -> {
			int index = viewLayout.getSelectedIndex();
			layout.setText(layout.getParent().getWidth() < 355 ? null : labels[index]);
			layout.setIcon(icon(icons[index]));
			if (index == 2 && (!inScope(appliedPieceIndex) || hiddenModelParts.get(appliedPieceIndex)))
				cyclePart(1);
			rebuildViewportLayout();
		});
		JButton view = new JButton("View", icon("handoff-view-camera"));
		var views = new WorkspaceMenu(170);
		addView(views, "Front", ViewportPanel::setViewFront);
		addView(views, "Side", ViewportPanel::setViewRight);
		addView(views, "Back", ViewportPanel::setViewBack);
		addView(views, "Top", ViewportPanel::setViewTop);
		view.addActionListener(e -> views.show(view.getParent(), 8, view.getY() + view.getHeight() + 4));

		JButton shading = new JButton("Shading", icon("handoff-shading"));
		shading.addActionListener(e -> {
			var menu = new WorkspaceMenu(170);
			menu.addToggle("Solid", solidVisible, on -> {
				solidVisible = on;
				eachView(pane -> pane.setShaded(on));
			});
			menu.addToggle("Colors", viewport.isShowFaceColors(), on -> eachView(pane -> pane.setShowFaceColors(on)));
			menu.addToggle("Wire", viewport.isShowWireframe(), on -> eachView(pane -> pane.setShowWireframe(on)));
			menu.addToggle("Vertices", viewport.isShowVertices(), on -> eachView(pane -> pane.setShowVertices(on)));
			menu.addToggle("Grid", gridVisible, on -> {
				gridVisible = on;
				eachView(pane -> pane.setShowGrid(on));
			});
			menu.show(shading.getParent(), 8, shading.getY() + shading.getHeight() + 4);
		});
		JButton previous = new JButton();
		JButton next = new JButton();
		setToolIcon(previous, "handoff-chevron-left", "Previous model");
		setToolIcon(next, "handoff-chevron-right", "Next model");
		previous.addActionListener(e -> cyclePart(-1));
		next.addActionListener(e -> cyclePart(1));
		JPanel bar = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 8));
		bar.setOpaque(false);
		bar.addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override
			public void componentResized(java.awt.event.ComponentEvent event) {
				boolean compact = bar.getWidth() < 355;
				layout.setText(compact ? null : labels[viewLayout.getSelectedIndex()]);
				view.setText(compact ? null : "View");
				shading.setText(compact ? null : "Shading");
				previous.setVisible(viewLayout.getSelectedIndex() == 2);
				next.setVisible(viewLayout.getSelectedIndex() == 2);
			}
		});
		viewLayout.addActionListener(e -> {
			previous.setVisible(viewLayout.getSelectedIndex() == 2);
			next.setVisible(viewLayout.getSelectedIndex() == 2);
			if (bar.getWidth() < 355)
				layout.setText(null);
		});
		for (JButton button : new JButton[] { layout, view, shading, previous, next }) {
			WorkspaceStyle.button(button, WorkspaceStyle.Button.TRIGGER);
			if (button != previous && button != next)
				WorkspaceStyle.dropdown(button);
			bar.add(button);
		}
		return bar;
	}

	private JPanel buildGroupBar() {
		var bar = new JPanel(new net.miginfocom.swing.MigLayout("insets 10px 12px 10px 16px, gap 0px, aligny center", "[]6px[]12px[1px!]4px[]4px[]", "[28px!]")) {
			@Override protected void paintComponent(java.awt.Graphics graphics) {
				var g = (java.awt.Graphics2D) graphics.create();
				g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
				for (int spread = 6; spread > 0; spread--) {
					g.setColor(new Color(0, 0, 0, 7));
					g.fillRoundRect(6 - spread, 6 - spread, getWidth() - 12 + spread * 2,
						getHeight() - 12 + spread * 2, 14 + spread * 2, 14 + spread * 2);
				}
				g.setColor(WorkspaceStyle.MENU);
				g.fillRoundRect(6, 6, getWidth() - 12, getHeight() - 12, 14, 14);
				g.setColor(WorkspaceStyle.BORDER);
				g.drawRoundRect(6, 6, getWidth() - 13, getHeight() - 13, 14, 14);
				g.dispose();
			}
		};
		bar.setOpaque(false);
		groupCount.setIcon(WorkspaceStyle.badge("", new Color(0xE8A33D)));
		groupCount.setFont(WorkspaceStyle.SEMIBOLD);
		groupCount.setForeground(new Color(0xE6E6E6));
		groupCount.setIconTextGap(6);
		groupScope.setForeground(new Color(0x7D7D7D));
		groupScope.setFont(WorkspaceStyle.UI.deriveFont(11f));
		WorkspaceStyle.button(groupAction, WorkspaceStyle.Button.PRIMARY);
		groupAction.setFont(WorkspaceStyle.SEMIBOLD);
		groupAction.addActionListener(e -> applyGroup());
		var clear = new JButton(WorkspaceStyle.icon("close", 12));
		WorkspaceStyle.button(clear, WorkspaceStyle.Button.TAB);
		clear.setPreferredSize(new Dimension(24, 24));
		clear.setToolTipText("Clear selection (Esc)");
		clear.addActionListener(e -> clearGroup());
		bar.add(groupCount);
		bar.add(groupScope);
		var divider = new JPanel();
		divider.setBackground(WorkspaceStyle.BORDER);
		bar.add(divider, "w 1px!, h 18px!");
		bar.add(groupAction, "h 26px!");
		bar.add(clear, "w 26px!, h 26px!");
		bar.registerKeyboardAction(e -> applyGroup(), javax.swing.KeyStroke.getKeyStroke("ENTER"), JComponent.WHEN_IN_FOCUSED_WINDOW);
		bar.registerKeyboardAction(e -> clearGroup(), javax.swing.KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
		bar.setVisible(false);
		return bar;
	}

	private void selectGroup(Set<Integer> indices, ViewportPanel.PickTarget target) {
		(target == ViewportPanel.PickTarget.FACE ? groupFaces : groupVertices).addAll(indices);
		updateGroupBar();
	}

	private void clearGroup() {
		groupVertices.clear();
		groupFaces.clear();
		updateGroupBar();
	}

	private void applyGroup() {
		if (groupBar == null || !groupBar.isShowing())
			return;
		if (viewport.getEditMode() == ViewportPanel.EditMode.SELECT) {
			viewport.setEditMode(ViewportPanel.EditMode.PLACE);
			return;
		}
		boolean add = viewport.getEditMode() == ViewportPanel.EditMode.PLACE;
		workspaceActions.edit((add ? "Add " : "Remove ") + "group lights", () -> {
			if (!groupVertices.isEmpty())
				callbacks.boxSelected(Set.copyOf(groupVertices), add, ViewportPanel.PickTarget.POINT);
			if (!groupFaces.isEmpty())
				callbacks.boxSelected(Set.copyOf(groupFaces), add, ViewportPanel.PickTarget.FACE);
		});
		clearGroup();
	}

	private void updateGroupBar() {
		if (groupBar == null)
			return;
		eachView(pane -> pane.setGroupSelection(groupVertices, groupFaces));
		groupBar.setVisible(!editingParticles && (!groupVertices.isEmpty() || !groupFaces.isEmpty()));
		if (!groupBar.isVisible())
			return;
		var counts = new java.util.ArrayList<String>(2);
		if (!groupVertices.isEmpty())
			counts.add(groupVertices.size() + (groupVertices.size() == 1 ? " vert" : " verts"));
		if (!groupFaces.isEmpty())
			counts.add(groupFaces.size() + (groupFaces.size() == 1 ? " face" : " faces"));
		groupCount.setText(String.join(" + ", counts));
		var pieces = new java.util.HashSet<Object>();
		if (snapshot != null) {
			for (int index : groupVertices)
				pieces.add(snapshot.pieceForVertex(index, appliedPieceIndex));
			for (int index : groupFaces)
				pieces.add(snapshot.pieceForFace(index, appliedPieceIndex));
		}
		pieces.remove(null);
		groupScope.setText(pieces.size() + (pieces.size() == 1 ? " model" : " models"));
		String definition = (String) lightBrushCombo.getSelectedItem();
		switch (viewport.getEditMode()) {
			case DELETE: groupAction.setText("Remove lights from group"); break;
			case PLACE: groupAction.setText(definition == null ? "Apply to group" : "Apply " + definition + " to group"); break;
			default: groupAction.setText("Switch to Place"); break;
		}
		groupBar.getParent().revalidate();
		groupBar.repaint();
	}

	private void eachView(java.util.function.Consumer<ViewportPanel> action) {
		action.accept(viewport);
		splitViews.forEach(action);
	}

	private void addView(WorkspaceMenu menu, String label, java.util.function.Consumer<ViewportPanel> action) {
		menu.addRow(label, null, "", () -> false, e -> eachView(action));
	}

	private void cyclePart(int direction) {
		if (snapshot == null || snapshot.getPieces().isEmpty())
			return;
		int first = appliedPieceIndex < 0 ? direction > 0 ? -1 : 0 : appliedPieceIndex;
		int count = snapshot.getPieces().size();
		for (int offset = 1; offset <= count; offset++) {
			int index = Math.floorMod(first + direction * offset, count);
			if (inScope(index) && !hiddenModelParts.get(index)) {
				selectPart(index);
				return;
			}
		}
	}

	private void selectPart(int index) {
		if (modelScope.equipmentParts.isEmpty())
			applyPiece(index);
		else
			applyPieceRow(modelScope, index);
	}

	private boolean inScope(int index) {
		return index >= 0 && (modelScope.pieceIndex < 0 || modelScope.containsPart(index));
	}

	private void selectModelScope(Row row) {
		modelScope = row;
		if (row.equipmentParts.size() > 1)
			expandedItems.add(row.itemId);
		clearGroup();
		rebuildModelRows();
		applyPieceRow(row, appliedPieceIndex);
		if (viewLayout.getSelectedIndex() == 2 && (!inScope(appliedPieceIndex) || hiddenModelParts.get(appliedPieceIndex)))
			cyclePart(1);
		rebuildViewportLayout();
		viewport.resetView();
	}

	private static void setToolIcon(javax.swing.AbstractButton button, String iconName, String label) {
		button.setIcon(icon(iconName));
		button.setText(null);
		button.setToolTipText(label);
		button.getAccessibleContext().setAccessibleName(label);
	}

	private boolean modelRowHidden(Row row) {
		return row.equipmentParts.isEmpty() ? hiddenModelParts.get(row.pieceIndex)
			: row.equipmentParts.stream().allMatch(hiddenModelParts::get);
	}

	private void toggleModelRow(int index) {
		if (index <= 0 || index >= modelRows.size())
			return;
		Row row = modelRows.get(index);
		boolean hidden = !modelRowHidden(row);
		clearGroup();
		if (row.equipmentParts.isEmpty())
			hiddenModelParts.set(row.pieceIndex, hidden);
		else
			row.equipmentParts.forEach(part -> hiddenModelParts.set(part, hidden));
		if (hidden && appliedPieceIndex >= 0 && hiddenModelParts.get(appliedPieceIndex)) {
			selectAnchor(null);
			if (viewLayout.getSelectedIndex() == 2)
				cyclePart(1);
		}
		rebuildViewportLayout();
		modelList.repaint();
	}

	private void rebuildViewportLayout() {
		var excluded = (java.util.BitSet) hiddenModelParts.clone();
		if (snapshot != null)
			for (int i = 0; i < snapshot.getPieces().size(); i++)
				if (!inScope(i))
					excluded.set(i);
		viewport.setHiddenPieces(excluded);
		viewport.setBreadcrumb(modelBreadcrumb());
		viewContent.removeAll();
		splitViews.clear();

		if (viewLayout.getSelectedIndex() != 1 || snapshot == null || editingParticles || modelScope.pieceIndex >= 0 && modelScope.equipmentParts.isEmpty()) {
			viewport.setPieceFilter(viewLayout.getSelectedIndex() == 2 ? appliedPieceIndex : -1);
			viewContent.add(viewport, BorderLayout.CENTER);
		} else {
			int count = snapshot.getPieces().size();
			int shown = count - excluded.cardinality();
			int columns = Math.max(1, Math.min(3, shown));
			var grid = new JPanel(new java.awt.GridLayout(0, columns, 3, 3));
			grid.setBackground(new Color(20, 20, 20));
			for (int i = 0; i < count; i++) {
				if (excluded.get(i))
					continue;
				final int index = i;
				var partView = new ViewportPanel((pick, action) -> {
					selectPart(index);
					pickModel(pick, action);
				}, (indices, add, target) -> {
					selectPart(index);
					selectGroup(indices, target);
				});
				partView.setOnEditModeChanged(viewport::setEditMode);
				partView.setOnPickTargetChanged(viewport::setPickTarget);
				partView.setShaded(solidVisible);
				partView.setShowGrid(gridVisible);
				partView.setShowFaceColors(viewport.isShowFaceColors());
				partView.setShowWireframe(viewport.isShowWireframe());
				partView.setShowVertices(viewport.isShowVertices());
				partView.setPickTarget(viewport.getPickTarget());
				partView.setEditMode(viewport.getEditMode());
				partView.setSnapshot(snapshot);
				partView.setPieceFilter(index);
				partView.setBreadcrumb(modelBreadcrumb());
				splitViews.add(partView);

				var pane = new JPanel(new BorderLayout());
				DevUi.themePanel(pane);
				pane.add(partView, BorderLayout.CENTER);
				var piece = snapshot.getPieces().get(i);
				String name = piece.getModelId() >= 0 ? "Model " + piece.getModelId() : "Part " + (i + 1);
				pane.add(panelHeader(name + "  /  " + piece.getFaces().length + " faces"), BorderLayout.SOUTH);
				grid.add(pane);
			}
			viewContent.add(grid, BorderLayout.CENTER);
		}
		refreshMarkers();
		if (recordingXs != null)
			applyScrub();
		updateGroupBar();
		viewContent.revalidate();
		viewContent.repaint();
	}

	private void showCard(String name) {
		workspaceLayout.show(workspaces, "definitions".equals(name) ? "definitions" : "scene");
		if (!"definitions".equals(name))
			cardLayout.show(cards, name);
		editingParticles = "particles".equals(name);
		toolRail.setGroups(workspaceTools.getOrDefault(name, List.of()));
		placementNavBtn.setSelected("placement".equals(name));
		definitionsNavBtn.setSelected("definitions".equals(name));
		particlesNavBtn.setSelected(editingParticles);
		vertexPickButton.setEnabled(!editingParticles);
		viewport.setFacePickingOnly(editingParticles);
		particlesPanel.setActive(editingParticles);
		if (editingParticles && viewLayout.getSelectedIndex() == 1)
			viewLayout.setSelectedIndex(2);
		rebuildViewportLayout();
		refreshMarkers();
	}

	private void updateLoadPanel() {
		loadPanel.setVisible(selectedMode > 0);
	}

	private void applyInSceneCaptureRow(int captureId) {
		selectedCaptureId = captureId;
		appliedPieceIndex = -1;
		viewport.setPieceFilter(-1);
		idField.setText(String.valueOf(captureId));
		if (captureId == lastAutoLoadedCaptureId)
			return;
		lastAutoLoadedCaptureId = captureId;
		switch (selectedMode) {
			case 0:
				callbacks.loadPlayer(captureId);
				break;
			case 1:
				callbacks.loadObject(captureId);
				break;
			case 2:
				callbacks.loadNpc(captureId);
				break;
			case 3:
				callbacks.loadGraphic(captureId);
				break;
		}
	}

	private void loadFromIdField() {
		String text = idField.getText().trim();
		int id = -1;
		if (!text.isEmpty()) {
			try {
				id = Integer.parseInt(text);
			} catch (NumberFormatException ignored) {
				return;
			}
		} else {
			int index = rowList.getSelectedIndex();
			if (index >= 0 && index < rows.size())
				id = rows.get(index).sightingId;
		}
		if (id < 0)
			return;
		lastAutoLoadedCaptureId = id;
		selectedCaptureId = id;
		appliedPieceIndex = -1;
		viewport.setPieceFilter(-1);
		switch (selectedMode) {
			case 1:
				callbacks.loadObject(id);
				break;
			case 2:
				callbacks.loadNpc(id);
				break;
			case 3:
				callbacks.loadGraphic(id);
				break;
		}
	}

	public void showHint(String text) {
		status.setText(text);
	}

	public void setLoading(boolean loading) {
		setCursor(loading ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR) : null);
		if (loading)
			status.setText("Capturing mesh…");
		else
			updateHint();
	}

	public void rebuildSightingsList() {
		rebuildRows();
	}

	public void setLightDescriptions(List<String> descriptions) {
		populating = true;
		String brush = (String) lightBrushCombo.getSelectedItem();
		String anchor = (String) anchorLightCombo.getSelectedItem();
		DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>(descriptions.toArray(new String[0]));
		lightBrushCombo.setModel(model);
		anchorLightCombo.setModel(new DefaultComboBoxModel<>(descriptions.toArray(new String[0])));
		if (brush != null)
			lightBrushCombo.setSelectedItem(brush);
		if (anchor != null)
			anchorLightCombo.setSelectedItem(anchor);
		refreshDefinitionList(selectedDefinitionId);
		populating = false;
	}

	public void refreshDefinitionList(@Nullable String selectId) {
		populating = true;
		definitionListModel.clear();
		String query = definitionSearch.getText().strip().toLowerCase(Locale.ROOT);
		var ordered = callbacks.getOrderedDefinitionNames();
		if (ordered.isEmpty())
			ordered = callbacks.getLightDescriptions();
		for (String desc : ordered)
			if (desc.toLowerCase(Locale.ROOT).contains(query))
				definitionListModel.addElement(desc);
		if (selectId != null && definitionListModel.contains(selectId))
			definitionList.setSelectedValue(selectId, true);
		else if (!definitionListModel.isEmpty())
			definitionList.setSelectedIndex(0);
		selectedDefinitionId = definitionList.getSelectedValue();
		populating = false;
		refreshDefinitionEditor();
	}

	private void refreshDefinitionEditor() {
		if (populating || selectedDefinitionId == null)
			return;
		populating = true;
		LightDefinition def = callbacks.getDefinition(selectedDefinitionId);
		defDescriptionField.setText(def.description != null ? def.description : "");
		radiusSpinner.setValue(def.radius);
		strengthSpinner.setValue((double) def.strength);
		heightSpinner.setValue(def.height);
		typeCombo.setSelectedItem(def.type != null ? def.type : LightType.STATIC);
		alignmentCombo.setSelectedItem(def.alignment != null ? def.alignment : Alignment.CUSTOM);
		float[] offset = def.offset != null && def.offset.length == 3 ? def.offset : new float[3];
		defOffsetXSpinner.setValue((double) offset[0]);
		defOffsetYSpinner.setValue((double) offset[1]);
		defOffsetZSpinner.setValue((double) offset[2]);
		durationSpinner.setValue((double) def.duration);
		rangeSpinner.setValue((double) def.range);
		fadeInSpinner.setValue(def.fadeInDuration);
		fadeOutSpinner.setValue(def.fadeOutDuration);
		spawnDelaySpinner.setValue(def.spawnDelay);
		despawnDelaySpinner.setValue(def.despawnDelay);
		fixedDespawnCheck.setSelected(def.fixedDespawnTime);
		despawnWithParentCheck.setSelected(def.despawnWithParent);
		visibleOtherPlanesCheck.setSelected(def.visibleFromOtherPlanes);
		ignoreActorHidingCheck.setSelected(def.ignoreActorHiding);
		waitForAnimationCheck.setSelected(def.waitForAnimation);
		renderableIndexSpinner.setValue(def.renderableIndex);
		animationIdsField.setText(formatIds(def.animationIds));
		npcIdsField.setText(formatIds(def.npcIds));
		objectIdsField.setText(formatIds(def.objectIds));
		projectileIdsField.setText(formatIds(def.projectileIds));
		graphicsIdsField.setText(formatIds(def.graphicsObjectIds));
		innerConeSpinner.setValue((double) def.innerConeAngle);
		outerConeSpinner.setValue((double) def.outerConeAngle);
		conePitchSpinner.setValue((double) def.conePitch);
		maskField.setText(def.mask == null ? "" : def.mask);
		maskScaleSpinner.setValue((double) def.maskScale);
		if (def.color != null && def.color.length == 3) {
			float[] srgb = ColorUtils.linearToSrgb(def.color);
			colorButton.setBackground(new Color(
				Math.round(srgb[0] * 255),
				Math.round(srgb[1] * 255),
				Math.round(srgb[2] * 255)
			));
		}
		populating = false;
	}

	private void saveDefinitionFromUi() {
		if (populating || selectedDefinitionId == null)
			return;
		LightDefinition def = callbacks.getDefinition(selectedDefinitionId);
		def.description = defDescriptionField.getText().trim();
		def.radius = ((Number) radiusSpinner.getValue()).intValue();
		def.strength = ((Number) strengthSpinner.getValue()).floatValue();
		def.height = ((Number) heightSpinner.getValue()).intValue();
		def.type = (LightType) typeCombo.getSelectedItem();
		def.alignment = (Alignment) alignmentCombo.getSelectedItem();
		def.offset = new float[] {
			((Number) defOffsetXSpinner.getValue()).floatValue(),
			((Number) defOffsetYSpinner.getValue()).floatValue(),
			((Number) defOffsetZSpinner.getValue()).floatValue()
		};
		def.duration = ((Number) durationSpinner.getValue()).floatValue();
		def.range = ((Number) rangeSpinner.getValue()).floatValue();
		def.fadeInDuration = ((Number) fadeInSpinner.getValue()).intValue();
		def.fadeOutDuration = ((Number) fadeOutSpinner.getValue()).intValue();
		def.spawnDelay = ((Number) spawnDelaySpinner.getValue()).intValue();
		def.despawnDelay = ((Number) despawnDelaySpinner.getValue()).intValue();
		def.fixedDespawnTime = fixedDespawnCheck.isSelected();
		def.despawnWithParent = despawnWithParentCheck.isSelected();
		def.visibleFromOtherPlanes = visibleOtherPlanesCheck.isSelected();
		def.ignoreActorHiding = ignoreActorHidingCheck.isSelected();
		def.waitForAnimation = waitForAnimationCheck.isSelected();
		def.renderableIndex = ((Number) renderableIndexSpinner.getValue()).intValue();
		def.animationIds = parseIds(animationIdsField.getText());
		def.npcIds = parseIds(npcIdsField.getText());
		def.objectIds = parseIds(objectIdsField.getText());
		def.projectileIds = parseIds(projectileIdsField.getText());
		def.graphicsObjectIds = parseIds(graphicsIdsField.getText());
		def.innerConeAngle = ((Number) innerConeSpinner.getValue()).floatValue();
		def.outerConeAngle = ((Number) outerConeSpinner.getValue()).floatValue();
		def.conePitch = ((Number) conePitchSpinner.getValue()).floatValue();
		String mask = maskField.getText().trim();
		def.mask = mask.isEmpty() ? null : mask;
		def.maskScale = ((Number) maskScaleSpinner.getValue()).floatValue();
		Color bg = colorButton.getBackground();
		def.color = ColorUtils.srgbToLinear(ColorUtils.srgb(bg.getRed(), bg.getGreen(), bg.getBlue()));
		workspaceActions.edit(selectedDefinitionId + " definition", () -> callbacks.saveDefinition(selectedDefinitionId, def));
		updateActiveLightMarker();
		if (!def.description.equals(selectedDefinitionId)) {
			selectedDefinitionId = def.description;
			refreshDefinitionList(selectedDefinitionId);
		}
	}

	private void pickDefinitionColor() {
		Color chosen = JColorChooser.showDialog(this, "Light color", colorButton.getBackground());
		if (chosen != null) {
			colorButton.setBackground(chosen);
			saveDefinitionFromUi();
		}
	}

	public void setSnapshot(@Nullable ModelSnapshot snapshot, @Nullable String targetRootLabel) {
		clearRecording();
		selectAnchor(null);
		releaseSnapshot();
		this.snapshot = snapshot;
		hiddenModelParts.clear();
		expandedItems.clear();
		modelScope = new Row(-1);
		appliedPieceIndex = -1;
		clearGroup();
		this.targetRootLabel = targetRootLabel;
		if (targetRootLabel != null)
			workspaceActions.reset(targetRootLabel);
		particlesPanel.select(null);
		if (snapshot != null)
			viewport.setSnapshot(snapshot);
		else
			viewport.clearSnapshot();
		rebuildModelRows();
		applyPieceRow(modelScope, -1);
		if (viewLayout.getSelectedIndex() == 2)
			cyclePart(1);
		rebuildRows();
		rebuildViewportLayout();
		refreshMarkers();
	}

	private void releaseSnapshot() {
		if (snapshot != null) {
			snapshot.release();
			snapshot = null;
		}
		viewport.clearSnapshot();
	}

	public void setRecording(float[][] xs, float[][] ys, float[][] zs, int[] frames) {
		if (snapshot == null || xs.length == 0 || xs[0].length != snapshot.getVertexCount())
			return;
		recordingXs = xs;
		recordingYs = ys;
		recordingZs = zs;
		recordingFrames = frames;
		scrubSlider.setMaximum(frames.length - 1);
		scrubSlider.setValue(0);
		scrubPanel.setVisible(true);
		applyScrub();
	}

	public void selectAnchor(@Nullable AnchorSelection anchor) {
		selectedAnchor = anchor;
		emptyLightLabel.setVisible(anchor == null);
		populating = true;
		if (anchor == null) {
			anchorPanel.setVisible(false);
			anchorTitle.setText("No anchor selected");
		} else {
			anchorPanel.setVisible(true);
			String kind = anchor.triangle ? "face" : "vert";
			if (snapshot != null && anchor.pieceIndex >= 0 && anchor.pieceIndex < snapshot.getPieces().size()) {
				var piece = snapshot.getPieces().get(anchor.pieceIndex);
				anchorTitle.setText(itemName(piece) + " · Model " + piece.getModelId() + " · " + kind + " " + anchor.localIndex);
			} else {
				anchorTitle.setText(kind + " " + anchor.localIndex);
			}
			String desc = callbacks.getAnchorLightDescription(anchor);
			if (desc != null)
				anchorLightCombo.setSelectedItem(desc);
			String defId = desc != null ? desc : (String) anchorLightCombo.getSelectedItem();
			if (defId != null) {
				selectedDefinitionId = defId;
				definitionList.setSelectedValue(defId, true);
				refreshDefinitionEditor();
			}
		}
		populating = false;
		loadPieceSettings();
		updateActiveLightMarker();
	}

	private void updateActiveLightMarker() {
		var anchor = selectedAnchor;
		if (editingParticles || snapshot == null || anchor == null || !inScope(anchor.pieceIndex)
			|| anchor.pieceIndex >= snapshot.getPieces().size() || hiddenModelParts.get(anchor.pieceIndex)) {
			eachView(pane -> pane.setActiveLight(null, null, null));
			return;
		}
		var piece = snapshot.getPieces().get(anchor.pieceIndex);
		int[] indices = anchor.triangle ? piece.getFaces() : piece.getVertices();
		if (anchor.localIndex < 0 || anchor.localIndex >= indices.length)
			return;
		int index = indices[anchor.localIndex];
		float[] bary = anchor.triangle ? callbacks.getAnchorBarycentric(anchor) : null;
		var pick = anchor.triangle
			? ViewportPanel.Pick.face(index, bary == null ? 1f / 3 : bary[0], bary == null ? 1f / 3 : bary[1], bary == null ? 1f / 3 : bary[2])
			: ViewportPanel.Pick.point(index);
		String name = callbacks.getAnchorLightDescription(anchor);
		Color resolved = name == null ? null : definitionColor(name);
		Color color = resolved == null ? new Color(0xFF9849) : resolved;
		float[] storedOffset = callbacks.getProfileOffset(anchor.pieceIndex);
		float[] offset = storedOffset == null ? new float[3] : storedOffset.clone();
		eachView(pane -> pane.setActiveLight(pick, color, offset));
	}

	public void refreshMarkers() {
		updateActiveLightMarker();
		modelLightCounts = new int[snapshot == null ? 0 : snapshot.getPieces().size()];
		var faces = new java.util.HashSet<Integer>();
		var vertices = new java.util.HashSet<Integer>();
		for (int i = 0; i < modelLightCounts.length; i++) {
			var pieceFaces = callbacks.litFaces(i);
			var pieceVertices = callbacks.litVertices(i);
			modelLightCounts[i] = pieceFaces.size() + pieceVertices.size();
			faces.addAll(pieceFaces);
			vertices.addAll(pieceVertices);
		}
		modelList.repaint();
		viewport.setSelectedFaces(editingParticles ? Set.of() : faces);
		viewport.setSelectedVertices(editingParticles ? Set.of() : vertices);
		for (ViewportPanel splitView : splitViews) {
			splitView.setSelectedFaces(faces);
			splitView.setSelectedVertices(vertices);
		}
	}
	public int getAppliedPieceIndex() {
		return appliedPieceIndex;
	}

	public String getPickLightDescription() {
		Object brush = lightBrushCombo.getSelectedItem();
		if (brush instanceof String && !((String) brush).isEmpty())
			return (String) brush;
		return "Torch";
	}

	public String getItemIdsFieldText() {
		return itemIdsField.getText();
	}

	public void rebuildPieceListRow(int pieceIndex) {
		rebuildModelRows();
	}

	private void selectCategory(int mode) {
		selectedMode = mode;
		selectedCaptureId = -1;
		lastAutoLoadedCaptureId = -1;
		idField.setText("");
		updateLoadPanel();
		rebuildRows();
		callbacks.refreshSightings();
	}

	private void rebuildRows() {
		rebuildingRows = true;
		rowListModel.clear();
		rows.clear();
		var byCategory = List.of(callbacks.getPlayerSightings(), callbacks.getObjectSightings(),
			callbacks.getNpcSightings(), callbacks.getGraphicSightings());
		for (int i = 0; i < categories.length; i++)
			categories[i].setText(CATEGORY_LABELS[i] + " " + byCategory.get(i).size());
		var sightings = byCategory.get(selectedMode);
		String query = browserSearch.getText().strip().toLowerCase(Locale.ROOT);
		int selected = -1;
		for (var sighting : sightings) {
			String label = sighting.formatRow();
			if (!label.toLowerCase(Locale.ROOT).contains(query))
				continue;
			if (sighting.id == selectedCaptureId)
				selected = rows.size();
			rows.add(new Row(-1, sighting.id));
			rowListModel.addElement(label);
		}
		rowList.setSelectedIndex(selected);
		boolean linked = !byCategory.get(0).isEmpty();
		clientStatus.setText(linked ? "Client linked" : "Waiting for scene");
		clientStatus.setIcon(linked ? WorkspaceStyle.badge("", new Color(0x6CB348)) : null);
		clientStatus.setForeground(linked ? new Color(108, 179, 72) : new Color(154, 154, 154));
		rebuildingRows = false;
	}

	private void rebuildModelRows() {
		rebuildingModels = true;
		modelRows.clear();
		modelListModel.clear();
		int count = snapshot == null ? 0 : snapshot.getPieces().size();
		modelRows.add(new Row(-1));
		modelListModel.addElement(targetRootLabel == null ? "Entity" : targetRootLabel);
		if (snapshot != null) {
			var equipment = new java.util.LinkedHashMap<Integer, List<Integer>>();
			for (int i = 0; i < count; i++) {
				var piece = snapshot.getPieces().get(i);
				if (piece.getItemId() >= 0)
					equipment.computeIfAbsent(piece.getItemId(), key -> new ArrayList<>()).add(i);
				else
					addModelRow(i);
			}
			for (var entry : equipment.entrySet()) {
				var parts = entry.getValue();
				var first = snapshot.getPieces().get(parts.get(0));
				modelRows.add(new Row(entry.getKey(), parts));

				modelListModel.addElement(itemName(first) + " (" + entry.getKey() + ")");
				if (expandedItems.contains(entry.getKey()) && parts.size() > 1)
					for (int part : parts)
						addModelRow(part);
			}
		}
		int selected = -1;
		for (int i = 0; i < modelRows.size(); i++) {
			Row row = modelRows.get(i);
			if (row.itemId == modelScope.itemId && row.pieceIndex == modelScope.pieceIndex) {
				selected = i;
				break;
			}
		}
		modelList.setSelectedIndex(selected);
		rebuildingModels = false;
	}

	private String modelRowMeta(Row row) {
		if (snapshot == null)
			return "0 models";
		if (row.pieceIndex < 0)
			return snapshot.getPieces().size() + " models";
		var piece = snapshot.getPieces().get(row.pieceIndex);
		if (row.equipmentParts.size() == 1)
			return piece.getModelId() >= 0 ? String.valueOf(piece.getModelId()) : "";
		int lights = 0;
		String meta;
		if (row.equipmentParts.isEmpty()) {
			meta = piece.getFaces().length + "f";
			if (row.pieceIndex < modelLightCounts.length)
				lights = modelLightCounts[row.pieceIndex];
		} else {
			meta = row.equipmentParts.size() + " models";
			for (int index : row.equipmentParts)
				if (index < modelLightCounts.length)
					lights += modelLightCounts[index];
		}
		return lights == 0 ? meta : meta + " · " + lights + "L";
	}

	private void addModelRow(int index) {
		var piece = snapshot.getPieces().get(index);
		modelRows.add(new Row(index));
		String name = piece.getModelId() >= 0 ? String.valueOf(piece.getModelId()) : "Part " + (index + 1);
		modelListModel.addElement(name);
	}

	private static String itemName(ModelSnapshot.Piece piece) {
		String label = piece.getLabel();
		int separator = label.indexOf(": ");
		return separator < 0 ? label : label.substring(separator + 2);
	}

	private String modelBreadcrumb() {
		if (snapshot == null || targetRootLabel == null)
			return "";
		String path = targetRootLabel;
		if (modelScope.itemId >= 0)
			path += " › " + itemName(snapshot.getPieces().get(modelScope.pieceIndex));
		if (appliedPieceIndex >= 0 && (viewLayout.getSelectedIndex() == 2 || modelScope.itemId < 0 && modelScope.pieceIndex >= 0)) {
			var piece = snapshot.getPieces().get(appliedPieceIndex);
			if (modelScope.itemId < 0 && piece.getItemId() >= 0)
				path += " › " + itemName(piece);
			path += " › " + (piece.getModelId() >= 0 ? piece.getModelId() : "Part " + (appliedPieceIndex + 1));
		}
		return path;
	}
	private void applyPieceRow(Row row, int preferredPiece) {
		if (row.equipmentParts.isEmpty())
			applyPiece(row.pieceIndex);
		else
			applyPiece(row.equipmentParts.contains(preferredPiece) ? preferredPiece : row.pieceIndex);
	}

	private void applyPiece(int index) {
		if (appliedPieceIndex != index)
			selectAnchor(null);
		appliedPieceIndex = index;
		viewport.setPieceFilter(viewLayout.getSelectedIndex() == 2 ? index : -1);
		viewport.setBreadcrumb(modelBreadcrumb());
		particlesPanel.select(snapshot != null && index >= 0 ? snapshot.getPieces().get(index) : null);
		loadPieceSettings();
		refreshMarkers();
		updateHint();
	}

	private int propertyPieceIndex() {
		return selectedAnchor == null ? appliedPieceIndex : selectedAnchor.pieceIndex;
	}

	private void loadPieceSettings() {
		int pieceIndex = propertyPieceIndex();
		boolean show = snapshot != null && pieceIndex >= 0 && pieceIndex < snapshot.getPieces().size();
		pieceNameField.setEnabled(show);
		itemIdsField.setEnabled(show);
		itemIdsField.setEditable(false);
		offsetXSpinner.setEnabled(show);
		offsetYSpinner.setEnabled(show);
		offsetZSpinner.setEnabled(show);
		if (!show)
			return;
		populating = true;
		pieceNameField.setText(callbacks.getPieceName(pieceIndex));
		itemIdsField.setText(callbacks.getPieceItemIdsText(pieceIndex));
		float[] offset = callbacks.getProfileOffset(pieceIndex);
		offsetXSpinner.setValue((double) offset[0]);
		offsetYSpinner.setValue((double) offset[1]);
		offsetZSpinner.setValue((double) offset[2]);
		populating = false;
	}

	private void applyPieceSettingsFromUi() {
		int pieceIndex = propertyPieceIndex();
		if (populating || pieceIndex < 0 || snapshot == null)
			return;
		workspaceActions.edit("Edit model " + pieceIndex, () -> callbacks.applyPieceSettings(pieceIndex, pieceNameField.getText(), itemIdsField.getText()));
		rebuildPieceListRow(pieceIndex);
	}

	private void updateHoverHint(@Nullable ViewportPanel.Pick pick) {
		if (pick == null) {
			updateHint();
			return;
		}
		String kind = pick.target == ViewportPanel.PickTarget.FACE ? "triangle" : "vertex";
		if (viewport.getEditMode() == ViewportPanel.EditMode.DELETE)
			editorHint.setText("Click or drag to remove " + kind + " " + pick.globalIndex);
		else
			editorHint.setText("Click " + kind + " " + pick.globalIndex + " to place · click lit anchor to edit");
	}

	private void updateHint() {
		status.setText(snapshot == null ? "Select a nearby entity." :
			targetRootLabel + "  /  " + snapshot.getPieces().size() + " models  /  " + snapshot.getFaceCount() + " faces");
		if (appliedPieceIndex >= 0) {
			String pick = viewport.getPickTarget() == ViewportPanel.PickTarget.FACE ? "face" : "vertex";
			editorHint.setText(viewport.getEditMode() == ViewportPanel.EditMode.DELETE ?
				"Click an anchor to remove it." : "Choose a light and click a " + pick + ".");
		} else if (selectedMode > 0) {
			editorHint.setText("Select a sighting and Load, or type an ID");
		} else if (snapshot != null) {
			editorHint.setText("Select a mesh piece, then place lights");
		} else {
			editorHint.setText("Refresh to capture the local player");
		}
	}

	private void applyScrub() {
		if (recordingXs == null)
			return;
		int i = Math.min(scrubSlider.getValue(), recordingXs.length - 1);
		eachView(view -> view.setPositionOverride(recordingXs[i], recordingYs[i], recordingZs[i]));
		scrubLabel.setText(recordingFrames != null && recordingFrames[i] >= 0
			? "frame " + recordingFrames[i] + " (" + (i + 1) + "/" + recordingFrames.length + ")"
			: "sample " + (i + 1) + "/" + recordingXs.length);
	}

	private void clearRecording() {
		recordingXs = null;
		recordingYs = null;
		recordingZs = null;
		recordingFrames = null;
		scrubPanel.setVisible(false);
		viewport.setPositionOverride(null, null, null);
	}

	private static JCheckBox styleCheck(String text) {
		JCheckBox check = new JCheckBox(text);
		check.setOpaque(false);
		check.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		check.setFont(DevUi.bodyFont(check.getFont()));
		return check;
	}

	private static String formatIds(@Nullable Set<Integer> ids) {
		if (ids == null || ids.isEmpty())
			return "";
		return ids.stream()
			.sorted()
			.map(String::valueOf)
			.collect(Collectors.joining(", "));
	}

	private static HashSet<Integer> parseIds(@Nullable String text) {
		HashSet<Integer> ids = new HashSet<>();
		if (text == null || text.trim().isEmpty())
			return ids;
		for (String part : text.split(",")) {
			String trimmed = part.trim();
			if (trimmed.isEmpty())
				continue;
			try {
				ids.add(Integer.parseInt(trimmed));
			} catch (NumberFormatException ignored) {}
		}
		return ids;
	}

	private static DocumentListener docListener(Runnable onChange) {
		return new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) { onChange.run(); }
			@Override
			public void removeUpdate(DocumentEvent e) { onChange.run(); }
			@Override
			public void changedUpdate(DocumentEvent e) { onChange.run(); }
		};
	}
}
