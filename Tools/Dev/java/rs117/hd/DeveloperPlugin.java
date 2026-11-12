package rs117.hd;

import com.google.inject.Provides;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import rs117.hd.dev.lights.ModelLightEditor;
import rs117.hd.dev.sceneloader.SceneLoader;
import rs117.hd.model.ModelExporter;
import rs117.hd.model.ModelImporter;
import rs117.hd.utils.Props;
import rs117.hd.utils.ResourcePath;

import static rs117.hd.utils.ResourcePath.path;

@Slf4j
@Singleton
@PluginDescriptor(
	name = "117 HD Developer",
	description = "development and profiling tools for 117 HD",
	tags = {"117", "hd", "developer", "development", "profiler"}
)
public class DeveloperPlugin extends Plugin {
	public static final ResourcePath MODEL_EXPORTS = Props.getFile(
		"rlhd.model-exports-path", () -> path("..", "model-exports"));

	@Inject
	private ClientThread clientThread;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private DeveloperConfig config;

	private ModelExporter modelExporter;
	private ModelImporter modelImporter;

	@Nullable
	private volatile ModelLightEditor modelEditor;
	@Nullable
	private NavigationButton modelEditorButton;

	// From 117 HD's injector, so it works with the running 117 HD's renderer, scene & managers
	@Nullable
	private volatile SceneLoader sceneLoader;

	@Provides
	DeveloperConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(DeveloperConfig.class);
	}

	@Override
	protected void startUp() {
		clientThread.invoke(() -> {
			var optional = pluginManager.getPlugins().stream()
				.filter(p -> p instanceof HdPlugin)
				.findFirst();
			if (optional.isEmpty())
				return false;

			HdPlugin plugin = (HdPlugin) optional.get();
			injector = plugin.getInjector();

			modelExporter = injector.getInstance(ModelExporter.class);
			modelImporter = injector.getInstance(ModelImporter.class);
			modelEditor = injector.getInstance(ModelLightEditor.class);
			SwingUtilities.invokeLater(this::addModelEditorButton);

			var loader = injector.getInstance(SceneLoader.class);
			sceneLoader = loader;
			if (config.cacheScene())
				loader.startUp();

			return true;
		});
	}

	@Override
	protected void shutDown() {
		var editor = modelEditor;
		modelEditor = null;
		SwingUtilities.invokeLater(() -> {
			if (modelEditorButton != null)
				clientToolbar.removeNavigation(modelEditorButton);
			modelEditorButton = null;
			if (editor != null)
				editor.close();
		});

		var loader = sceneLoader;
		sceneLoader = null;
		if (loader != null)
			loader.shutDown();

		clientThread.invoke(() -> {
			if (modelImporter != null)
				modelImporter.close();
			modelImporter = null;
			if (modelExporter != null)
				modelExporter.close();
			modelExporter = null;
		});
	}

	private void addModelEditorButton() {
		var editor = modelEditor;
		if (editor == null || modelEditorButton != null)
			return;
		try (var icon = ModelLightEditor.class.getResourceAsStream("/rs117/hd/dev/editor/icons/editor-icon-32.png")) {
			if (icon == null)
				throw new IOException("Missing model editor icon");
			modelEditorButton = NavigationButton.builder()
				.tooltip("117HD Editor")
				.icon(ImageIO.read(icon))
				.priority(3)
				.onClick(editor::open)
				.build();
			clientToolbar.addNavigation(modelEditorButton);
		} catch (IOException ex) {
			log.warn("Unable to add the 117HD Editor to the toolbar", ex);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!event.getGroup().equals(DeveloperConfig.CONFIG_GROUP))
			return;

		if (event.getKey().equals(DeveloperConfig.KEY_CACHE_SCENE)) {
			var loader = sceneLoader;
			if (loader == null)
				return;
			if (config.cacheScene()) {
				loader.startUp();
			} else {
				loader.shutDown();
			}
		}
	}
}
