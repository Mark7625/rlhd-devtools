package rs117.hd.dev.lights;

import rs117.hd.dev.editor.EditorSplash;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import rs117.hd.scene.LightManager;
import rs117.hd.scene.lights.LightDefinition;
import rs117.hd.scene.model.ModelLightManager;
import rs117.hd.scene.model.ModelLightProfile;
import rs117.hd.scene.model.ModelLightStore;
import rs117.hd.scene.model.ModelSnapshot;
import rs117.hd.scene.model.TriangleAnchor;

@Singleton
@Slf4j
public class ModelLightEditor implements LightViewerFrame.Callbacks {
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private ModelLightManager modelLightManager;

	@Inject
	private ModelLightStore store;

	@Inject
	private LightManager lightManager;

	@Inject
	private LightMeshCapture meshCapture;

	@Nullable
	private LightViewerFrame frame;
	private EditorSplash startupSplash;

	@Nullable
	private ModelSnapshot viewerSnapshot;

	private boolean eventBusRegistered;
	private int captureMode;
	private int viewerPlayerId = -1;
	private int viewerObjectId = -1;
	private int viewerNpcId = -1;
	private int viewerGraphicId = -1;

	private int recordTicksLeft;
	@Nullable
	private ModelSnapshot recordSnapshot;
	private final java.util.List<float[]> recordXs = new java.util.ArrayList<>();
	private final java.util.List<float[]> recordYs = new java.util.ArrayList<>();
	private final java.util.List<float[]> recordZs = new java.util.ArrayList<>();
	private final java.util.List<Integer> recordFrames = new java.util.ArrayList<>();

	private final Map<Integer, String> pieceProfileKeys = new HashMap<>();
	private List<LightMeshCapture.Sighting> cachedPlayerSightings = List.of();
	private List<LightMeshCapture.Sighting> cachedGraphicSightings = List.of();
	private List<LightMeshCapture.Sighting> cachedObjectSightings = List.of();
	private List<LightMeshCapture.Sighting> cachedNpcSightings = List.of();

	@Getter
	private String selectedLightDescription = "Torch";

	public void close() {
		assert SwingUtilities.isEventDispatchThread();
		if (startupSplash != null) {
			startupSplash.dispose();
			startupSplash = null;
		}
		if (eventBusRegistered) {
			eventBus.unregister(this);
			eventBusRegistered = false;
		}
		store.setImportListener(null);
		modelLightManager.setLightDefinitionsListener(null);
		recordTicksLeft = 0;
		if (frame != null)
			frame.dispose();
		frame = null;
		viewerSnapshot = null;
	}

	public void open() {
		openViewer(null);
	}

	public void openViewer(@Nullable String profileKey) {
		SwingUtilities.invokeLater(() -> {
			if (startupSplash != null) {
				startupSplash.toFront();
				return;
			}
			if (frame != null) {
				showEditor();
				frame.setLightDescriptions(modelLightManager.getAvailableLightDescriptions());
				refreshSnapshot();
				return;
			}
			startEditor();
		});
	}

	private void startEditor() {
		var splash = new EditorSplash(() -> {
			startupSplash = null;
			showEditor();
		}, () -> {
			startupSplash = null;
			startEditor();
		}, () -> startupSplash = null);
		startupSplash = splash;
		splash.step("Connecting to RuneLite client");
		splash.setVisible(true);
		clientThread.invokeLater(() -> {
			try {
				SwingUtilities.invokeLater(() -> splash.step("Reading editor preferences"));
				var definitions = modelLightManager.getAvailableLightDescriptions();
				SwingUtilities.invokeLater(() -> splash.step("Loading light definitions"));
				scanSightings();
				SwingUtilities.invokeLater(() -> {
					try {
						if (startupSplash != splash)
							return;
						splash.step("Scanning nearby scene");
						frame = new LightViewerFrame(this);
						frame.setLightDescriptions(definitions);
						store.setImportListener(() -> SwingUtilities.invokeLater(this::onStoreImported));
						modelLightManager.setLightDefinitionsListener(
							() -> SwingUtilities.invokeLater(this::refreshLightDescriptions));
						if (!eventBusRegistered) {
							eventBus.register(this);
							eventBusRegistered = true;
						}
						splash.step("Preparing renderer");
						clientThread.invokeLater(() -> {
							try {
								captureSnapshotForStartup(splash);
							} catch (RuntimeException e) {
								SwingUtilities.invokeLater(() -> failStartup(splash, e));
							}
						});
					} catch (RuntimeException e) {
						failStartup(splash, e);
					}
				});
			} catch (RuntimeException e) {
				SwingUtilities.invokeLater(() -> failStartup(splash, e));
			}
		});
	}

	private void showEditor() {
		if (frame != null) {
			frame.setVisible(true);
			frame.toFront();
		}
	}

	private void failStartup(EditorSplash splash, RuntimeException error) {
		if (startupSplash != splash)
			return;
		log.warn("Unable to start the 117HD Editor", error);
		if (frame != null) {
			frame.dispose();
			frame = null;
		}
		splash.fail();
	}

	private void onStoreImported() {
		if (frame != null)
			frame.refreshMarkers();
	}

	private void refreshLightDescriptions() {
		if (frame != null) {
			frame.setLightDescriptions(modelLightManager.getAvailableLightDescriptions());
			frame.refreshDefinitionList(null);
		}
	}

	@Override
	public void refreshSightings() {
		clientThread.invokeLater(() -> {
			scanSightings();
			SwingUtilities.invokeLater(() -> {
				if (frame != null)
					frame.rebuildSightingsList();
			});
		});
	}

	@Override
	public void refreshSnapshot() {
		SwingUtilities.invokeLater(() -> {
			if (frame != null)
				frame.setLoading(true);
		});
		clientThread.invokeLater(() -> {
			scanSightings();
			captureSnapshot();
		});
	}

	private void captureSnapshot() {
		captureSnapshotForStartup(null);
	}

	private void captureSnapshotForStartup(@Nullable EditorSplash splash) {
		LightMeshCapture.CaptureResult capture;
		switch (captureMode) {
			case 1:
				capture = viewerObjectId >= 0
					? meshCapture.captureObject(viewerObjectId)
					: meshCapture.capturePlayer(viewerPlayerId);
				break;
			case 2:
				capture = viewerNpcId >= 0
					? meshCapture.captureNpc(viewerNpcId)
					: meshCapture.capturePlayer(viewerPlayerId);
				break;
			case 3:
				capture = viewerGraphicId >= 0
					? meshCapture.captureGraphic(viewerGraphicId)
					: meshCapture.capturePlayer(viewerPlayerId);
				break;
			default:
				capture = meshCapture.capturePlayer(viewerPlayerId);
		}
		LightMeshCapture.CaptureResult result = capture;
		SwingUtilities.invokeLater(() -> {
			if (frame == null || splash != null && startupSplash != splash)
				return;
			try {
				frame.setLoading(false);
				frame.rebuildSightingsList();
				if (result == null) {
					viewerSnapshot = null;
					pieceProfileKeys.clear();
					frame.setSnapshot(null, null);
					frame.showHint("Could not verify the model parts. Try Refresh once the model loads.");
					return;
				}
				viewerSnapshot = result.snapshot;
				pieceProfileKeys.clear();
				if (result.startRecording)
					startRecording();
				frame.setSnapshot(viewerSnapshot, result.targetLabel);
			} catch (RuntimeException e) {
				if (splash == null)
					throw e;
				failStartup(splash, e);
			} finally {
				if (splash != null && frame != null)
					splash.finish();
			}
		});
	}

	@Override
	public void playerViewSelected() {
		captureMode = 0;
		viewerPlayerId = -1;
		viewerObjectId = -1;
		viewerNpcId = -1;
		viewerGraphicId = -1;
	}

	@Override
	public void loadPlayer(int playerId) {
		captureMode = 0;
		viewerPlayerId = playerId;
		viewerObjectId = -1;
		viewerNpcId = -1;
		viewerGraphicId = -1;
		clientThread.invokeLater(this::captureSnapshot);
	}

	private void scanSightings() {
		cachedPlayerSightings = meshCapture.playerSightings();
		cachedObjectSightings = meshCapture.objectSightings();
		cachedNpcSightings = meshCapture.npcSightings();
		cachedGraphicSightings = meshCapture.graphicSightings();
	}

	@Override
	public List<LightMeshCapture.Sighting> getPlayerSightings() {
		return cachedPlayerSightings;
	}

	@Override
	public List<LightMeshCapture.Sighting> getGraphicSightings() {
		return cachedGraphicSightings;
	}

	@Override
	public void loadObject(int objectId) {
		if (captureMode == 1 && viewerObjectId == objectId && viewerSnapshot != null)
			return;
		captureMode = 1;
		viewerObjectId = objectId;
		viewerNpcId = -1;
		viewerGraphicId = -1;
		SwingUtilities.invokeLater(() -> {
			if (frame != null)
				frame.setLoading(true);
		});
		clientThread.invokeLater(this::captureSnapshot);
	}

	@Override
	public void loadNpc(int npcId) {
		if (captureMode == 2 && viewerNpcId == npcId && viewerSnapshot != null)
			return;
		captureMode = 2;
		viewerNpcId = npcId;
		viewerObjectId = -1;
		viewerGraphicId = -1;
		SwingUtilities.invokeLater(() -> {
			if (frame != null)
				frame.setLoading(true);
		});
		clientThread.invokeLater(this::captureSnapshot);
	}

	@Override
	public void loadGraphic(int graphicId) {
		if (captureMode == 3 && viewerGraphicId == graphicId && viewerSnapshot != null)
			return;
		captureMode = 3;
		viewerGraphicId = graphicId;
		viewerObjectId = -1;
		viewerNpcId = -1;
		SwingUtilities.invokeLater(() -> {
			if (frame != null)
				frame.setLoading(true);
		});
		clientThread.invokeLater(this::captureSnapshot);
	}

	@Override
	public void poseAnimation(int animId) {
		if (viewerNpcId < 0) {
			if (frame != null)
				frame.selectAnchor(null);
			JOptionPane.showMessageDialog(
				frame,
				"Pose needs an NPC snapshot. Load an NPC first.",
				"Pose",
				JOptionPane.INFORMATION_MESSAGE
			);
			return;
		}
		// NPC cache posing can be added later; for now use live recording
		refreshSnapshot();
	}

	@Override
	public List<LightMeshCapture.Sighting> getObjectSightings() {
		return cachedObjectSightings;
	}

	@Override
	public List<LightMeshCapture.Sighting> getNpcSightings() {
		return cachedNpcSightings;
	}

	@Override
	public Set<Integer> litFaces(int pieceIndex) {
		if (viewerSnapshot == null)
			return Set.of();
		if (pieceIndex >= 0) {
			ModelSnapshot.Piece piece = viewerSnapshot.getPieces().get(pieceIndex);
			String profileKey = resolveProfileKey(pieceIndex, piece);
			if (profileKey == null)
				return Set.of();
			ModelLightProfile profile = store.get(profileKey);
			if (profile == null)
				return Set.of();
			Set<Integer> out = new HashSet<>();
			for (int localFace : profile.triangleIndices()) {
				if (localFace >= 0 && localFace < piece.getFaces().length)
					out.add(piece.getFaces()[localFace]);
			}
			return out;
		}
		Set<Integer> out = new HashSet<>();
		for (int i = 0; i < viewerSnapshot.getPieces().size(); i++)
			out.addAll(litFaces(i));
		return out;
	}

	@Override
	public Set<Integer> litVertices(int pieceIndex) {
		if (viewerSnapshot == null)
			return Set.of();
		if (pieceIndex >= 0) {
			ModelSnapshot.Piece piece = viewerSnapshot.getPieces().get(pieceIndex);
			String profileKey = resolveProfileKey(pieceIndex, piece);
			if (profileKey == null)
				return Set.of();
			ModelLightProfile profile = store.get(profileKey);
			if (profile == null)
				return Set.of();
			Set<Integer> out = new HashSet<>();
			for (var entry : profile.getVertices().entrySet()) {
				int local = entry.getKey();
				if (local >= 0 && local < piece.getVertices().length)
					out.add(piece.getVertices()[local]);
			}
			return out;
		}
		Set<Integer> out = new HashSet<>();
		for (int i = 0; i < viewerSnapshot.getPieces().size(); i++)
			out.addAll(litVertices(i));
		return out;
	}

	@Subscribe
	public void onGameTick(GameTick event) {
		if (recordTicksLeft <= 0)
			return;
		sampleRecording();
	}

	private void startRecording() {
		releaseRecordSnapshot();
		recordXs.clear();
		recordYs.clear();
		recordZs.clear();
		recordFrames.clear();
		if (client.getLocalPlayer() == null)
			return;
		recordTicksLeft = 150;
	}

	private void sampleRecording() {
		recordTicksLeft--;
		Player local = client.getLocalPlayer();
		Model model = local == null ? null : local.getModel();
		int frameNum = local == null ? -1 : local.getAnimationFrame();

		if (model == null && !recordXs.isEmpty())
			recordTicksLeft = 0;

		if (model != null) {
			if (recordSnapshot == null)
				recordSnapshot = ModelSnapshot.capture(model);
			if (model.getVerticesCount() != recordSnapshot.getVertexCount()) {
				recordTicksLeft = 0;
				releaseRecordSnapshot();
				recordXs.clear();
				recordYs.clear();
				recordZs.clear();
				recordFrames.clear();
				return;
			}
			int count = recordSnapshot.getVertexCount();
			recordXs.add(Arrays.copyOf(model.getVerticesX(), count));
			recordYs.add(Arrays.copyOf(model.getVerticesY(), count));
			recordZs.add(Arrays.copyOf(model.getVerticesZ(), count));
			recordFrames.add(frameNum);
		}

		if (recordTicksLeft == 0)
			finishRecording();
	}

	private void finishRecording() {
		ModelSnapshot snapshot = recordSnapshot;
		recordSnapshot = null;
		if (snapshot == null || recordXs.isEmpty())
			return;

		float[][] xs = recordXs.toArray(new float[0][]);
		float[][] ys = recordYs.toArray(new float[0][]);
		float[][] zs = recordZs.toArray(new float[0][]);
		int[] frames = new int[recordFrames.size()];
		for (int i = 0; i < frames.length; i++)
			frames[i] = recordFrames.get(i);
		recordXs.clear();
		recordYs.clear();
		recordZs.clear();
		recordFrames.clear();

		SwingUtilities.invokeLater(() -> {
			if (frame != null)
				frame.setRecording(xs, ys, zs, frames);
		});
		snapshot.release();
	}

	private void releaseRecordSnapshot() {
		if (recordSnapshot != null) {
			recordSnapshot.release();
			recordSnapshot = null;
		}
	}

	@Override
	public void pickClicked(ViewportPanel.Pick pick, ViewportPanel.PickAction action) {
		if (viewerSnapshot == null || frame == null)
			return;

		if (action == ViewportPanel.PickAction.SELECT) {
			selectAnchorFromPick(pick);
			return;
		}

		if (pick.target == ViewportPanel.PickTarget.FACE)
			handleFacePick(pick, action);
		else
			handlePointPick(pick, action);
	}

	private void selectAnchorFromPick(ViewportPanel.Pick pick) {
		int preferredPiece = frame.getAppliedPieceIndex();
		if (pick.target == ViewportPanel.PickTarget.FACE) {
			ModelSnapshot.Piece piece = viewerSnapshot.pieceForFace(pick.globalIndex, preferredPiece);
			if (piece == null)
				return;
			int localFace = piece.localFaceIndex(pick.globalIndex);
			if (localFace < 0)
				return;
			int pieceIndex = viewerSnapshot.getPieces().indexOf(piece);
			frame.selectAnchor(new LightViewerFrame.AnchorSelection(pieceIndex, localFace, true));
		} else {
			ModelSnapshot.Piece piece = viewerSnapshot.pieceForVertex(pick.globalIndex, preferredPiece);
			if (piece == null)
				return;
			int local = piece.localIndexOf(pick.globalIndex);
			if (local < 0)
				return;
			int pieceIndex = viewerSnapshot.getPieces().indexOf(piece);
			frame.selectAnchor(new LightViewerFrame.AnchorSelection(pieceIndex, local, false));
		}
	}

	private void handleFacePick(ViewportPanel.Pick pick, ViewportPanel.PickAction action) {
		int preferredPiece = frame.getAppliedPieceIndex();
		ModelSnapshot.Piece piece = viewerSnapshot.pieceForFace(pick.globalIndex, preferredPiece);
		if (piece == null)
			return;

		int localFace = piece.localFaceIndex(pick.globalIndex);
		if (localFace < 0)
			return;

		String profileKey = profileKeyFor(piece);
		if (profileKey == null)
			return;

		if (action == ViewportPanel.PickAction.ADD) {
			if (!store.hasTriangle(profileKey, localFace)) {
				store.toggleTriangle(profileKey, localFace, pick.bary0, pick.bary1, pick.bary2);
				store.setTriangleLightDescription(profileKey, localFace, pickLightDescription());
			}
			frame.refreshMarkers();
			selectAnchorFromPick(pick);
			return;
		}

		if (!store.hasTriangle(profileKey, localFace))
			return;

		if (action == ViewportPanel.PickAction.REMOVE) {
			store.toggleTriangle(profileKey, localFace, pick.bary0, pick.bary1, pick.bary2);
			frame.selectAnchor(null);
		} else if (action == ViewportPanel.PickAction.CHANGE) {
			store.setTriangleLightDescription(profileKey, localFace, pickLightDescription());
			selectAnchorFromPick(pick);
		}
		frame.refreshMarkers();
	}

	private void handlePointPick(ViewportPanel.Pick pick, ViewportPanel.PickAction action) {
		int preferredPiece = frame.getAppliedPieceIndex();
		ModelSnapshot.Piece piece = viewerSnapshot.pieceForVertex(pick.globalIndex, preferredPiece);
		if (piece == null)
			return;

		int local = piece.localIndexOf(pick.globalIndex);
		if (local < 0)
			return;

		String profileKey = profileKeyFor(piece);
		if (profileKey == null)
			return;

		if (action == ViewportPanel.PickAction.ADD) {
			if (!store.hasVertex(profileKey, local)) {
				store.toggleVertex(profileKey, local);
				store.setVertexLightDescription(profileKey, local, pickLightDescription());
			}
			frame.refreshMarkers();
			selectAnchorFromPick(pick);
			return;
		}

		if (!store.hasVertex(profileKey, local))
			return;

		if (action == ViewportPanel.PickAction.REMOVE) {
			store.toggleVertex(profileKey, local);
			frame.selectAnchor(null);
		} else if (action == ViewportPanel.PickAction.CHANGE) {
			store.setVertexLightDescription(profileKey, local, pickLightDescription());
			selectAnchorFromPick(pick);
		}
		frame.refreshMarkers();
	}

	@Override
	public void boxSelected(Set<Integer> globalIndices, boolean add, ViewportPanel.PickTarget target) {
		if (viewerSnapshot == null || frame == null)
			return;

		int preferredPiece = frame.getAppliedPieceIndex();
		if (target == ViewportPanel.PickTarget.FACE) {
			for (int globalFace : globalIndices) {
				ModelSnapshot.Piece piece = viewerSnapshot.pieceForFace(globalFace, preferredPiece);
				if (piece == null)
					continue;
				int localFace = piece.localFaceIndex(globalFace);
				if (localFace < 0)
					continue;
				String profileKey = profileKeyFor(piece);
				if (profileKey == null)
					continue;
				if (add) {
					if (!store.hasTriangle(profileKey, localFace)) {
						store.toggleTriangle(profileKey, localFace, 1f / 3f, 1f / 3f, 1f / 3f);
						store.setTriangleLightDescription(profileKey, localFace, pickLightDescription());
					}
				} else if (store.hasTriangle(profileKey, localFace)) {
					store.toggleTriangle(profileKey, localFace, 1f / 3f, 1f / 3f, 1f / 3f);
				}
			}
		} else {
			for (int globalVertex : globalIndices) {
				ModelSnapshot.Piece piece = viewerSnapshot.pieceForVertex(globalVertex, preferredPiece);
				if (piece == null)
					continue;
				int local = piece.localIndexOf(globalVertex);
				if (local < 0)
					continue;
				String profileKey = profileKeyFor(piece);
				if (profileKey == null)
					continue;
				if (add) {
					if (!store.hasVertex(profileKey, local)) {
						store.toggleVertex(profileKey, local);
						store.setVertexLightDescription(profileKey, local, pickLightDescription());
					}
				} else if (store.hasVertex(profileKey, local)) {
					store.toggleVertex(profileKey, local);
				}
			}
		}
		frame.refreshMarkers();
	}

	@Override
	public void anchorSelected(LightViewerFrame.AnchorSelection anchor) {
		if (frame != null)
			frame.selectAnchor(anchor);
	}

	@Override
	public List<String> getLightDescriptions() {
		return modelLightManager.getAvailableLightDescriptions();
	}

	@Override
	public List<String> getOrderedDefinitionNames() {
		synchronized (lightManager) {
			return java.util.Arrays.stream(definitionTemplates()).map(definition -> definition.description)
				.filter(java.util.Objects::nonNull).distinct().collect(java.util.stream.Collectors.toList());
		}
	}

	@Override
	public void onLightBrushChanged(String description) {
		if (description != null && !description.isEmpty())
			selectedLightDescription = description;
	}

	@Override
	public String getPieceName(int pieceIndex) {
		ModelLightProfile profile = profileForPiece(pieceIndex);
		if (profile != null && profile.getName() != null && !profile.getName().isEmpty())
			return profile.getName();
		if (viewerSnapshot != null && pieceIndex >= 0 && pieceIndex < viewerSnapshot.getPieces().size()) {
			String label = viewerSnapshot.getPieces().get(pieceIndex).getLabel();
			if (label != null)
				return label;
		}
		return "Piece " + (pieceIndex + 1);
	}

	@Override
	public String getPieceItemIdsText(int pieceIndex) {
		if (viewerSnapshot != null && pieceIndex >= 0 && pieceIndex < viewerSnapshot.getPieces().size()) {
			int itemId = viewerSnapshot.getPieces().get(pieceIndex).getItemId();
			if (itemId >= 0)
				return Integer.toString(itemId);
		}
		ModelLightProfile profile = profileForPiece(pieceIndex);
		if (profile == null || profile.getItemIds().isEmpty())
			return "";
		return profile.getItemIds().stream()
			.sorted()
			.map(String::valueOf)
			.collect(Collectors.joining(", "));
	}

	@Override
	public void applyPieceSettings(int pieceIndex, String name, String itemIdsText) {
		if (viewerSnapshot == null || pieceIndex < 0 || pieceIndex >= viewerSnapshot.getPieces().size())
			return;

		ModelSnapshot.Piece piece = viewerSnapshot.getPieces().get(pieceIndex);
		String trimmedName = name == null ? "" : name.trim();
		String defaultName = trimmedName.isEmpty() ? "Piece " + (pieceIndex + 1) : trimmedName;
		String profileKey;

		if (captureMode == 1 && viewerObjectId >= 0) {
			@Nullable String existingKey = store.findObjectProfileKey(piece.getMeshKey(), viewerObjectId);
			if (existingKey != null) {
				profileKey = existingKey;
				store.setObjectIds(profileKey, Set.of(viewerObjectId));
			} else {
				profileKey = store.ensureProfileForObject(piece.getMeshKey(), defaultName, viewerObjectId);
			}
			store.rename(profileKey, trimmedName.isEmpty() ? null : trimmedName);
		} else if (captureMode == 2 && viewerNpcId >= 0) {
			@Nullable String existingKey = store.findNpcProfileKey(piece.getMeshKey(), viewerNpcId);
			if (existingKey != null) {
				profileKey = existingKey;
				store.setNpcIds(profileKey, Set.of(viewerNpcId));
			} else {
				profileKey = store.ensureProfileForNpc(piece.getMeshKey(), defaultName, viewerNpcId);
			}
			store.rename(profileKey, trimmedName.isEmpty() ? null : trimmedName);
		} else {
			if (piece.getItemId() < 0)
				return;
			profileKey = store.ensureEquipmentProfile(piece, getPieceName(pieceIndex));
			if (profileKey == null) {
				frame.showHint("The equipment part's face count changed. Delete its old profile before re-anchoring.");
				return;
			}
			store.rename(profileKey, trimmedName.isEmpty() ? null : trimmedName);
		}

		pieceProfileKeys.put(pieceIndex, profileKey);
		if (frame != null) {
			frame.refreshMarkers();
			frame.rebuildPieceListRow(pieceIndex);
		}
	}

	@Override
	public void applyProfileOffset(int pieceIndex, float x, float y, float z) {
		String profileKey = profileKeyForPiece(pieceIndex);
		if (profileKey != null)
			store.setProfileOffset(profileKey, x, y, z);
	}

	@Override
	public float[] getProfileOffset(int pieceIndex) {
		ModelLightProfile profile = profileForPiece(pieceIndex);
		if (profile == null)
			return new float[3];
		return new float[] { profile.getOffsetX(), profile.getOffsetY(), profile.getOffsetZ() };
	}

	@Override
	public String getPieceListLabel(int pieceIndex) {
		if (viewerSnapshot == null || pieceIndex < 0 || pieceIndex >= viewerSnapshot.getPieces().size())
			return "Piece " + (pieceIndex + 1);

		ModelSnapshot.Piece piece = viewerSnapshot.getPieces().get(pieceIndex);
		String name = getPieceName(pieceIndex);
		if (piece.getItemId() >= 0) {
			int[] faces = piece.getFaces();
			String partLabel = piece.getLabel().equals(name) ? name : name + " | " + piece.getLabel();
			return partLabel + " | item " + piece.getItemId() + " | model " + piece.getModelId() +
				" | faces " + faces[0] + "–" + faces[faces.length - 1];
		}
		String itemSuffix = formatItemSuffix(profileForPiece(pieceIndex));
		return name + " (" + piece.getVertices().length + "v, " + piece.getFaces().length + "f)" + itemSuffix;
	}

	@Override
	@Nullable
	public String getAnchorLightDescription(LightViewerFrame.AnchorSelection anchor) {
		String profileKey = profileKeyForPiece(anchor.pieceIndex);
		if (profileKey == null)
			return null;
		ModelLightProfile profile = store.get(profileKey);
		if (profile == null)
			return null;
		if (anchor.triangle)
			return profile.lightDescriptionForTriangle(anchor.localIndex);
		return profile.lightDescriptionForVertex(anchor.localIndex);
	}

	@Override
	public void setAnchorLightDescription(LightViewerFrame.AnchorSelection anchor, String description) {
		String profileKey = profileKeyForPiece(anchor.pieceIndex);
		if (profileKey == null)
			return;
		if (anchor.triangle)
			store.setTriangleLightDescription(profileKey, anchor.localIndex, description);
		else
			store.setVertexLightDescription(profileKey, anchor.localIndex, description);
		if (frame != null)
			frame.refreshMarkers();
	}

	@Override
	@Nullable
	public float[] getAnchorBarycentric(LightViewerFrame.AnchorSelection anchor) {
		if (!anchor.triangle)
			return null;
		String profileKey = profileKeyForPiece(anchor.pieceIndex);
		if (profileKey == null)
			return null;
		ModelLightProfile profile = store.get(profileKey);
		if (profile == null)
			return null;
		TriangleAnchor tri = profile.getTriangles().get(anchor.localIndex);
		if (tri == null)
			return null;
		return new float[] { tri.getBary0(), tri.getBary1(), tri.getBary2() };
	}

	@Override
	public void setAnchorBarycentric(LightViewerFrame.AnchorSelection anchor, float b0, float b1, float b2) {
		if (!anchor.triangle)
			return;
		String profileKey = profileKeyForPiece(anchor.pieceIndex);
		if (profileKey != null)
			store.setTriangleBarycentric(profileKey, anchor.localIndex, b0, b1, b2);
	}

	@Override
	public LightDefinition getDefinition(String description) {
		return lightManager.copyDefinitionForEditor(description);
	}

	@Override
	public void saveDefinition(String description, LightDefinition definition) {
		lightManager.updateDefinitionTemplate(description, definition);
		if (frame != null)
			frame.setLightDescriptions(modelLightManager.getAvailableLightDescriptions());
	}

	@Override
	public void createDefinition(String description) {
		LightDefinition def = new LightDefinition();
		def.description = description;
		def.color = new float[] { 1f, 0.8f, 0.4f };
		lightManager.addDefinitionTemplate(def);
		if (frame != null)
			frame.setLightDescriptions(modelLightManager.getAvailableLightDescriptions());
	}

	@Override
	public void deleteDefinition(String description) {
		lightManager.removeDefinitionTemplate(description);
		if (frame != null)
			frame.setLightDescriptions(modelLightManager.getAvailableLightDescriptions());
	}

	@Override
	public void mirrorAnchor(LightViewerFrame.AnchorSelection anchor, boolean zAxis) {
		if (viewerSnapshot == null || frame == null || anchor.pieceIndex < 0
			|| anchor.pieceIndex >= viewerSnapshot.getPieces().size())
			return;
		var piece = viewerSnapshot.getPieces().get(anchor.pieceIndex);
		int[] vertices = piece.getVertices();
		if (vertices.length == 0)
			return;
		float[] xs = viewerSnapshot.getVerticesX();
		float[] ys = viewerSnapshot.getVerticesY();
		float[] zs = viewerSnapshot.getVerticesZ();
		float x, y, z;
		if (anchor.triangle) {
			int face = piece.getFaces()[anchor.localIndex];
			int a = viewerSnapshot.getFaceIndices1()[face];
			int b = viewerSnapshot.getFaceIndices2()[face];
			int c = viewerSnapshot.getFaceIndices3()[face];
			float[] weights = getAnchorBarycentric(anchor);
			x = xs[a] * weights[0] + xs[b] * weights[1] + xs[c] * weights[2];
			y = ys[a] * weights[0] + ys[b] * weights[1] + ys[c] * weights[2];
			z = zs[a] * weights[0] + zs[b] * weights[1] + zs[c] * weights[2];
		} else {
			int v = vertices[anchor.localIndex];
			x = xs[v];
			y = ys[v];
			z = zs[v];
		}
		double center = 0;
		for (int v : vertices)
			center += zAxis ? zs[v] : xs[v];
		center /= vertices.length;
		if (zAxis)
			z = (float) (2 * center - z);
		else
			x = (float) (2 * center - x);
		int nearest = -1;
		double nearestDistance = Double.POSITIVE_INFINITY;
		for (int i = 0; i < vertices.length; i++) {
			int v = vertices[i];
			double dx = xs[v] - x, dy = ys[v] - y, dz = zs[v] - z;
			double distance = dx * dx + dy * dy + dz * dz;
			if (distance < nearestDistance) {
				nearestDistance = distance;
				nearest = i;
			}
		}
		String key = resolveProfileKey(anchor.pieceIndex, piece);
		if (key == null || nearest < 0 || store.hasVertex(key, nearest))
			return;
		String definition = getAnchorLightDescription(anchor);
		store.toggleVertex(key, nearest);
		store.setVertexLightDescription(key, nearest, definition);
		frame.selectAnchor(new LightViewerFrame.AnchorSelection(anchor.pieceIndex, nearest, false));
		frame.refreshMarkers();
	}

	private static final com.google.gson.Gson WORKSPACE_GSON = new com.google.gson.Gson();
	private static final java.lang.reflect.Type PROFILE_MAP = new com.google.gson.reflect.TypeToken<Map<String, ModelLightProfile>>() {}.getType();

	@Override
	public com.google.gson.JsonObject captureEditorData() {
		var data = new com.google.gson.JsonObject();
		data.add("profiles", WORKSPACE_GSON.toJsonTree(store.snapshotAll(), PROFILE_MAP));
		var definitions = new com.google.gson.JsonArray();
		synchronized (lightManager) {
			for (LightDefinition definition : definitionTemplates())
				definitions.add(encodeDefinition(definition));
		}
		data.add("definitions", definitions);
		return data;
	}

	@Override
	@SuppressWarnings("unchecked")
	public void restoreEditorData(com.google.gson.JsonObject data) {
		Map<String, ModelLightProfile> restored = WORKSPACE_GSON.fromJson(data.get("profiles"), PROFILE_MAP);
		var encoded = data.getAsJsonArray("definitions");
		LightDefinition[] definitions = encoded == null ? null : new LightDefinition[encoded.size()];
		if (encoded != null)
			for (int i = 0; i < encoded.size(); i++)
				definitions[i] = decodeDefinition(encoded.get(i).getAsJsonObject());
		if (restored == null || definitions == null || java.util.Arrays.stream(definitions).anyMatch(d -> d == null || d.description == null))
			throw new IllegalArgumentException("Invalid lights editor snapshot");
		synchronized (store) {
			try {
				var field = ModelLightStore.class.getDeclaredField("profiles");
				var notify = ModelLightStore.class.getDeclaredMethod("notifyChanged");
				field.setAccessible(true);
				notify.setAccessible(true);
				var profiles = (Map<String, ModelLightProfile>) field.get(store);
				profiles.clear();
				profiles.putAll(restored);
				notify.invoke(store);
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Unable to restore model lights", e);
			}
		}
		replaceDefinitionTemplates(definitions);
		if (frame != null)
			frame.setLightDescriptions(getLightDescriptions());
	}

	@Override
	public void duplicateDefinition(String source, String name) {
		synchronized (lightManager) {
			var definitions = new java.util.ArrayList<>(java.util.Arrays.asList(definitionTemplates()));
			for (int i = 0; i < definitions.size(); i++) {
				if (!source.equals(definitions.get(i).description))
					continue;
				LightDefinition copy = decodeDefinition(encodeDefinition(definitions.get(i)));
				copy.description = name;
				definitions.add(i + 1, copy);
				replaceDefinitionTemplates(definitions.toArray(new LightDefinition[0]));
				break;
			}
		}
		if (frame != null)
			frame.setLightDescriptions(getLightDescriptions());
	}

	private static com.google.gson.JsonObject encodeDefinition(LightDefinition definition) {
		var data = new com.google.gson.JsonObject();
		try {
			for (var field : LightDefinition.class.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic())
					continue;
				field.setAccessible(true);
				data.add(field.getName(), WORKSPACE_GSON.toJsonTree(field.get(definition), field.getGenericType()));
			}
			return data;
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Unable to capture light definition", e);
		}
	}

	private static LightDefinition decodeDefinition(com.google.gson.JsonObject data) {
		var definition = new LightDefinition();
		try {
			for (var field : LightDefinition.class.getDeclaredFields()) {
				if (!data.has(field.getName()) || java.lang.reflect.Modifier.isStatic(field.getModifiers()) || field.isSynthetic())
					continue;
				field.setAccessible(true);
				field.set(definition, WORKSPACE_GSON.fromJson(data.get(field.getName()), field.getGenericType()));
			}
			return definition;
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Unable to restore light definition", e);
		}
	}

	private LightDefinition[] definitionTemplates() {
		try {
			var field = rs117.hd.scene.LightManager.class.getDeclaredField("definitionArray");
			field.setAccessible(true);
			return (LightDefinition[]) field.get(lightManager);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Unable to read light definitions", e);
		}
	}

	private void replaceDefinitionTemplates(LightDefinition[] definitions) {
		synchronized (lightManager) {
			try {
				var type = rs117.hd.scene.LightManager.class;
				var templates = type.getDeclaredField("definitionArray");
				var masks = type.getDeclaredField("lightMaskManager");
				var rebuild = type.getDeclaredMethod("rebuildDescriptionIndex");
				templates.setAccessible(true);
				masks.setAccessible(true);
				rebuild.setAccessible(true);
				var maskManager = (rs117.hd.scene.lights.LightMaskManager) masks.get(lightManager);
				maskManager.rebuildFromDefinitions(java.util.Arrays.asList(definitions));
				templates.set(lightManager, definitions);
				rebuild.invoke(lightManager);
				modelLightManager.onLightDefinitionsChanged();
			} catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Unable to restore light definitions", e);
			}
		}
	}

	@Override
	public void saveModelLights() {
		try {
			var path = store.saveToDisk();
			String resourcePath = "src/main/resources/rs117/hd/scene/model/model_lights.json";
			String message = path.equals(ModelLightStore.getResourcePath())
				? "Saved to:\n" + path + "\n\nCommit this file to ship the changes."
				: "Exported to:\n" + path
					+ "\n\nCopy into " + resourcePath + " and rebuild to ship the changes.";
			JOptionPane.showMessageDialog(frame, message, "Model lights saved", JOptionPane.INFORMATION_MESSAGE);
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
				frame,
				"Failed to save model lights:\n" + ex.getMessage(),
				"Save failed",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	@Override
	public void saveLightDefinitions() {
		try {
			lightManager.saveDefinitionsToDisk();
			String path = LightManager.getLightsResourcePath().toString();
			JOptionPane.showMessageDialog(
				frame,
				"Saved to:\n" + path + "\n\nCommit lights.json to ship the changes.",
				"Lights saved",
				JOptionPane.INFORMATION_MESSAGE
			);
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(
				frame,
				"Failed to save lights.json:\n" + ex.getMessage(),
				"Save failed",
				JOptionPane.ERROR_MESSAGE
			);
		}
	}

	public Map<String, ModelLightProfile> getProfiles() {
		return store.snapshotAll();
	}

	public void setProfileEnabled(String profileKey, boolean enabled) {
		store.setEnabled(profileKey, enabled);
	}

	public void setAllProfilesEnabled(boolean enabled) {
		store.setAllEnabled(enabled);
	}

	private String pickLightDescription() {
		return frame != null ? frame.getPickLightDescription() : selectedLightDescription;
	}

	private static boolean isEquipmentProfile(ModelLightProfile profile) {
		return !profile.isNpcProfile() && !profile.isObjectProfile();
	}

	@Nullable
	private String profileKeyFor(ModelSnapshot.Piece piece) {
		int pieceIndex = viewerSnapshot != null ? viewerSnapshot.getPieces().indexOf(piece) : -1;
		if (pieceIndex >= 0) {
			@Nullable String key = resolveProfileKey(pieceIndex, piece);
			if (key != null)
				return key;
		}

		String fallback = pieceIndex >= 0 ? "Piece " + (pieceIndex + 1) : piece.getVertices().length + "v";
		if (captureMode == 1 && viewerObjectId >= 0)
			return store.ensureProfileForObject(piece.getMeshKey(), fallback, viewerObjectId);
		if (captureMode == 2 && viewerNpcId >= 0)
			return store.ensureProfileForNpc(piece.getMeshKey(), fallback, viewerNpcId);
		if (piece.getItemId() < 0)
			return null;
		String key = store.ensureEquipmentProfile(piece, piece.getLabel());
		if (key == null && frame != null)
			frame.showHint("The equipment part's face count changed. Delete its old profile before re-anchoring.");
		return key;
	}

	@Nullable
	private String profileKeyForPiece(int pieceIndex) {
		if (viewerSnapshot == null || pieceIndex < 0 || pieceIndex >= viewerSnapshot.getPieces().size())
			return null;
		return resolveProfileKey(pieceIndex, viewerSnapshot.getPieces().get(pieceIndex));
	}

	@Nullable
	private ModelLightProfile profileForPiece(int pieceIndex) {
		@Nullable String profileKey = profileKeyForPiece(pieceIndex);
		return profileKey == null ? null : store.get(profileKey);
	}

	@Nullable
	private String resolveProfileKey(int pieceIndex, ModelSnapshot.Piece piece) {
		@Nullable String cached = pieceProfileKeys.get(pieceIndex);
		if (cached != null) {
			ModelLightProfile cachedProfile = store.get(cached);
			if (cachedProfile != null
				&& piece.getMeshKey().equals(cachedProfile.getMeshKey())
				&& matchesCaptureTarget(cachedProfile)
				&& (piece.getItemId() < 0 || cachedProfile.getExpectedFaceCount() == piece.getFaces().length))
				return cached;
		}

		if (captureMode == 1 && viewerObjectId >= 0) {
			@Nullable String exact = store.findObjectProfileKey(piece.getMeshKey(), viewerObjectId);
			if (exact != null) {
				pieceProfileKeys.put(pieceIndex, exact);
				return exact;
			}
			for (Map.Entry<String, ModelLightProfile> entry : store.snapshotAll().entrySet()) {
				ModelLightProfile profile = entry.getValue();
				if (!piece.getMeshKey().equals(profile.getMeshKey())
					|| profile.isNpcProfile()
					|| !profile.getItemIds().isEmpty()
					|| !profile.getObjectIds().isEmpty()
					|| (profile.getVertices().isEmpty() && profile.getTriangles().isEmpty()))
					continue;
				store.setObjectIds(entry.getKey(), Set.of(viewerObjectId));
				pieceProfileKeys.put(pieceIndex, entry.getKey());
				return entry.getKey();
			}
		} else if (captureMode == 2 && viewerNpcId >= 0) {
			@Nullable String exact = store.findNpcProfileKey(piece.getMeshKey(), viewerNpcId);
			if (exact != null) {
				pieceProfileKeys.put(pieceIndex, exact);
				return exact;
			}
		} else if (piece.getItemId() >= 0) {
			String key = store.findProfileKey(piece.getMeshKey(), Set.of(piece.getItemId()));
			ModelLightProfile profile = key == null ? null : store.get(key);
			if (profile != null && profile.getExpectedFaceCount() == piece.getFaces().length) {
				pieceProfileKeys.put(pieceIndex, key);
				return key;
			}
		}
		return null;
	}

	private boolean matchesCaptureTarget(ModelLightProfile profile) {
		if (captureMode == 1 && viewerObjectId >= 0)
			return profile.isObjectProfile() && profile.getObjectIds().contains(viewerObjectId);
		if (captureMode == 2 && viewerNpcId >= 0)
			return profile.isNpcProfile() && profile.getNpcIds().contains(viewerNpcId);
		return isEquipmentProfile(profile);
	}

	private static String formatItemSuffix(@Nullable ModelLightProfile profile) {
		if (profile == null || profile.getItemIds().isEmpty())
			return "";
		String ids = profile.getItemIds().stream()
			.sorted()
			.map(String::valueOf)
			.collect(Collectors.joining(", "));
		return " [item " + ids + "]";
	}
}
