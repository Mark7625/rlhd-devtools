package rs117.hd.dev.editor;

import java.awt.*;
import java.awt.font.TextAttribute;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.swing.*;

public final class EditorSplash extends JWindow {
	private static final int WIDTH = 640;
	private static final int HEIGHT = 400;
	private static final int STEP_COUNT = 6;
	private final Image logo = image("icons/editor-icon-256.png");
	private final Image wordmark = image("117hd-wordmark.png");
	private final String version = buildVersion();
	private final List<String> completed = new ArrayList<>();
	private final long started = System.nanoTime();
	private final Timer animation = new Timer(16, e -> tick());
	private final Runnable openEditor;
	private final Runnable retry;
	private final Runnable closeEditor;
	private final JPanel card = new JPanel(null) {
		@Override protected void paintComponent(Graphics graphics) {
			super.paintComponent(graphics);
			paintCard(graphics);
		}
	};

	private String status = "";
	private int step;
	private boolean failed;
	private long transition;
	private long readyAt;
	private double from;
	private double progress;

	public EditorSplash(Runnable openEditor, Runnable retry, Runnable closeEditor) {
		this.openEditor = openEditor;
		this.retry = retry;
		this.closeEditor = closeEditor;
		setIconImages(appIcons());
		setSize(WIDTH, HEIGHT);
		setLocationRelativeTo(null);
		setAlwaysOnTop(true);
		if (supports(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSPARENT))
			setShape(new RoundRectangle2D.Double(0, 0, WIDTH, HEIGHT, 20, 20));
		card.setBackground(new Color(0x161616));
		setContentPane(card);
		var drag = new java.awt.event.MouseAdapter() {
			private Point pressed;
			@Override public void mousePressed(java.awt.event.MouseEvent e) { pressed = e.getPoint(); }
			@Override public void mouseDragged(java.awt.event.MouseEvent e) {
				if (pressed != null)
					setLocation(e.getXOnScreen() - pressed.x, e.getYOnScreen() - pressed.y);
			}
		};
		card.addMouseListener(drag);
		card.addMouseMotionListener(drag);
		animation.start();
	}

	public static List<Image> appIcons() {
		var images = new ArrayList<Image>();
		for (int size : new int[] {16, 24, 32, 48, 64, 128, 256, 512})
			images.add(image("icons/editor-icon-" + size + ".png"));
		return images;
	}

	private static Image image(String name) {
		return new ImageIcon(EditorSplash.class.getResource("/rs117/hd/dev/editor/" + name)).getImage();
	}

	private static String buildVersion() {
		var properties = new Properties();
		try (var stream = EditorSplash.class.getResourceAsStream("/rs117/hd/dev/editor/editor-build.properties")) {
			if (stream != null)
				properties.load(stream);
		} catch (IOException ignored) {
		}
		String value = properties.getProperty("version");
		return value == null ? "dev build" : "v" + value + " · dev build";
	}

	public void step(String message) {
		if (!status.isEmpty())
			completed.add(status);
		status = message;
		step = Math.min(step + 1, STEP_COUNT);
		from = progress;
		transition = System.nanoTime();
	}

	public void finish() {
		step("Ready");
		readyAt = Math.max(started + 600_000_000L, System.nanoTime() + 800_000_000L);
	}

	public void fail() {
		failed = true;
		var retryButton = new JButton("Retry");
		WorkspaceStyle.button(retryButton, WorkspaceStyle.Button.PRIMARY);
		retryButton.setBounds(542, 312, 58, 24);
		retryButton.addActionListener(e -> {
			dispose();
			retry.run();
		});
		card.add(retryButton);
		var close = new JButton("Close");
		WorkspaceStyle.button(close, WorkspaceStyle.Button.SECONDARY);
		close.setBounds(474, 312, 62, 24);
		close.addActionListener(e -> {
			dispose();
			closeEditor.run();
		});
		card.add(close);
		card.repaint();
	}

	private void tick() {
		long now = System.nanoTime();
		double t = Math.min(1, (now - transition) / 500_000_000.0);
		progress = from + (step / (double) STEP_COUNT - from) * (1 - Math.pow(1 - t, 3));
		if (readyAt != 0 && now >= readyAt) {
			float fade = (now - readyAt) / 150_000_000f;
			if (fade >= 1 || !supports(GraphicsDevice.WindowTranslucency.TRANSLUCENT)) {
				dispose();
				openEditor.run();
				return;
			}
			setOpacity(1 - fade);
		}
		card.repaint();
	}

	private boolean supports(GraphicsDevice.WindowTranslucency mode) {
		return getGraphicsConfiguration().getDevice().isWindowTranslucencySupported(mode);
	}

	@Override public void dispose() {
		animation.stop();
		super.dispose();
	}

	private void paintCard(Graphics graphics) {
		var g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		double seconds = (System.nanoTime() - started) / 1e9;
		glow(g, -120, 40, 420, 320, new Color(79, 182, 255, 26));
		glow(g, 340, 40, 420, 320, new Color(255, 165, 61, 26));
		double pulse = failed ? 0 : (1 - Math.cos(seconds * Math.PI * 2 / 2.4)) / 2;
		double glowScale = .92 + .13 * pulse;
		glow(g, 320 - 80 * glowScale, 72 - 70 * glowScale, 160 * glowScale, 140 * glowScale,
			new Color(255, 190, 110, (int) (45 + pulse * 37)));
		g.drawImage(logo, 276, 47, 88, 88, null);
		g.drawImage(wordmark, 190, 149, 260, 92, null);
		g.setFont(WorkspaceStyle.SEMIBOLD.deriveFont(11f).deriveFont(Map.of(TextAttribute.TRACKING, .32f)));
		g.setColor(WorkspaceStyle.DIM);
		String title = "HD EDITOR";
		g.drawString(title, (WIDTH - g.getFontMetrics().stringWidth(title)) / 2, 260);

		if (failed)
			paintError(g);
		else
			paintProgress(g, seconds);
		g.setColor(new Color(0x222222));
		g.drawLine(0, 372, WIDTH, 372);
		g.setFont(WorkspaceStyle.MONO.deriveFont(10f));
		g.setColor(new Color(0x6A6A6A));
		g.drawString(version, 14, 390);
		String mode = failed ? "In-game · startup error" : "In-game · client linked";
		int modeX = WIDTH - 14 - g.getFontMetrics().stringWidth(mode);
		g.drawString(mode, modeX, 390);
		g.setColor(failed ? new Color(0xE06060) : new Color(0x6CB348));
		g.fillOval(modeX - 12, 383, 6, 6);
		g.setColor(WorkspaceStyle.HEADER);
		g.drawRoundRect(0, 0, WIDTH - 1, HEIGHT - 1, 20, 20);
		g.dispose();
	}

	private void paintProgress(Graphics2D g, double seconds) {
		g.setFont(WorkspaceStyle.UI);
		g.setColor(new Color(0xD6D6D6));
		g.drawString(status + (readyAt == 0 ? "…" : ""), 32, 311);
		g.setFont(WorkspaceStyle.MONO);
		g.setColor(new Color(0x7D7D7D));
		String count = step + " / " + STEP_COUNT;
		g.drawString(count, WIDTH - 32 - g.getFontMetrics().stringWidth(count), 311);
		var track = new RoundRectangle2D.Double(32, 323, 576, 4, 4, 4);
		g.setColor(WorkspaceStyle.PANEL);
		g.fill(track);
		int fill = (int) (576 * progress);
		if (fill > 0) {
			g.setPaint(new LinearGradientPaint(32, 323, 32 + fill, 323, new float[] {0, .55f, 1},
				new Color[] {new Color(0x4FB6FF), new Color(0xECE7DF), new Color(0xFFA53D)}));
			g.fillRoundRect(32, 323, fill, 4, 4, 4);
		}
		var oldClip = g.getClip();
		g.clip(track);
		float x = (float) (32 - 230 + (seconds % 1.6) / 1.6 * 806);
		g.setPaint(new LinearGradientPaint(x, 323, x + 230, 323, new float[] {0, .5f, 1},
			new Color[] {new Color(255, 255, 255, 0), new Color(255, 255, 255, 89), new Color(255, 255, 255, 0)}));
		g.fillRect((int) x, 323, 230, 4);
		g.setClip(oldClip);
		g.clipRect(32, 332, 576, 20);
		g.setFont(WorkspaceStyle.MONO.deriveFont(10f));
		g.setColor(new Color(0x5F5F5F));
		int doneX = 32;
		for (int i = Math.max(0, completed.size() - 3); i < completed.size(); i++) {
			g.drawLine(doneX + 1, 342, doneX + 3, 344);
			g.drawLine(doneX + 3, 344, doneX + 7, 339);
			String label = completed.get(i);
			g.drawString(label, doneX + 12, 346);
			doneX += 28 + g.getFontMetrics().stringWidth(label);
		}
		g.setClip(oldClip);
	}

	private void paintError(Graphics2D g) {
		g.setColor(WorkspaceStyle.MENU);
		g.fillRoundRect(32, 300, 576, 48, 12, 12);
		g.setColor(new Color(0x3A2626));
		g.drawRoundRect(32, 300, 575, 47, 12, 12);
		g.setColor(new Color(0xE06060));
		g.fillOval(44, 320, 8, 8);
		g.setFont(WorkspaceStyle.SEMIBOLD);
		g.setColor(WorkspaceStyle.TEXT);
		g.drawString("Couldn't start the 117HD Editor", 64, 320);
		g.setFont(WorkspaceStyle.UI.deriveFont(11f));
		g.setColor(WorkspaceStyle.DIM);
		g.drawString("Make sure 117 HD is enabled, then retry.", 64, 335);
	}

	private static void glow(Graphics2D graphics, double x, double y, double width, double height, Color color) {
		var g = (Graphics2D) graphics.create();
		g.translate(x, y);
		g.scale(width, height);
		g.setPaint(new RadialGradientPaint(.5f, .5f, .5f, new float[] {0, 1},
			new Color[] {color, new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)}));
		g.fill(new java.awt.geom.Ellipse2D.Double(0, 0, 1, 1));
		g.dispose();
	}
}
