package me.whereareiam.anvil.integration.intellij;

import com.intellij.ide.ui.laf.LookAndFeelThemeAdapter;
import com.intellij.ide.ui.laf.LookAndFeelThemeAdapterKt;
import com.intellij.ide.ui.laf.UiThemeProviderListManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.UIUtil;

import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;

import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.tooling.api.model.process.ProcessDefinition;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;
import me.whereareiam.anvil.tooling.api.type.ProcessRole;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class WindowTestSupport {
	public static void useDarcula(Disposable owner) throws Exception {
		useTheme(owner, true);
	}

	public static void useTheme(Disposable owner, boolean dark) throws Exception {
		useTheme(owner, dark ? "ExperimentalDark" : "ExperimentalLight", dark);
	}

	public static void useTheme(Disposable owner, String themeId, boolean dark) throws Exception {
		var previousLookAndFeel = UIManager.getLookAndFeel();
		boolean previousDark = !JBColor.isBright();
		var colors = EditorColorsManager.getInstance();
		var previousScheme = colors.getGlobalScheme();
		// HeadlessLafManager has no themes; install the same bundled theme adapter used by the IDE.
		var theme = UiThemeProviderListManager.Companion.getInstance().findThemeById(themeId);
		UIManager.setLookAndFeel(
				new LookAndFeelThemeAdapter(LookAndFeelThemeAdapterKt.createBaseLaF(), theme));
		JBColor.setDark(dark);
		IconLoader.setUseDarkIcons(dark);
		colors.setGlobalScheme(colors.getScheme(dark ? "Darcula" : "Default"));
		IconLoader.activate();
		Disposer.register(
				owner,
				() -> {
					try {
						theme.dispose();
						UIManager.setLookAndFeel(previousLookAndFeel);
						JBColor.setDark(previousDark);
						IconLoader.setUseDarkIcons(previousDark);
						colors.setGlobalScheme(previousScheme);
					} catch (Exception failure) {
						throw new AssertionError(failure);
					}
				});
	}

	public static boolean actionEnabled(Container root, String text) {
		ActionState action = action(root, text);
		return action != null && action.event.getPresentation().isEnabled();
	}

	public static void performAction(Container root, String text) {
		ActionState action = action(root, text);
		assertTrue(
				action != null && action.event.getPresentation().isEnabled(),
				"Missing enabled native action: " + text);
		action.action.actionPerformed(action.event);
	}

	private static ActionState action(Container root, String text) {
		if (root instanceof ActionToolbar toolbar) {
			for (AnAction candidate : toolbar.getActionGroup().getChildren(null)) {
				if (candidate instanceof Separator) continue;
				AnActionEvent event =
						AnActionEvent.createEvent(
								candidate,
								DataContext.EMPTY_CONTEXT,
								null,
								"Anvil.Test",
								ActionUiKind.TOOLBAR,
								null);
				candidate.update(event);
				if (text.equals(event.getPresentation().getText()))
					return new ActionState(candidate, event);
			}
		}
		for (Component component : root.getComponents())
			if (component instanceof Container container) {
				ActionState action = action(container, text);
				if (action != null) return action;
			}
		return null;
	}

	private record ActionState(AnAction action, AnActionEvent event) {}

	public static ScenarioSource source() {
		return ScenarioSource.builder()
				.id("fixture:main")
				.displayName("Example plugin")
				.integrationId("fixture-window")
				.directory(Path.of("/example"))
				.build();
	}

	public static ScenarioDescriptor scenario() {
		return ScenarioDescriptor.builder()
				.definition("example.AuthenticationScenarios")
				.name("registration")
				.displayName("Player registration")
				.description("New and returning players can register and sign in.")
				.category("Authentication")
				.tag("smoke")
				.entrypoint("proxy")
				.javaRequirement("Java 21")
				.startupTimeoutMillis(90000)
				.process(
						ProcessDefinition.builder()
								.name("paper")
								.displayName("Paper backend")
								.description("Stores player data.")
								.role(ProcessRole.SERVER)
								.platform("paper")
								.distribution("Pinned Paper distribution")
								.distributionVersion("1.21.11")
								.distributionBuild("117")
								.memoryMegabytes(1024)
								.onlineMode(false)
								.javaRequirement("Java 21 (scenario)")
								.build())
				.process(
						ProcessDefinition.builder()
								.name("proxy")
								.displayName("Velocity proxy")
								.description("Routes incoming players.")
								.role(ProcessRole.PROXY)
								.platform("velocity")
								.distribution("Pinned Velocity distribution")
								.distributionVersion("3.5.0")
								.memoryMegabytes(512)
								.onlineMode(false)
								.javaRequirement("Java 21 (scenario)")
								.backendName("paper")
								.defaultBackend("paper")
								.build())
				.build();
	}

	public static void await(BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			UIUtil.dispatchAllInvocationEvents();
			Thread.sleep(5);
		}
		assertTrue(condition.getAsBoolean(), "Timed out waiting for the Anvil view");
	}

	public static <T extends Component> T find(Container root, Class<T> type) {
		for (Component component : root.getComponents()) {
			if (type.isInstance(component)) return type.cast(component);
			if (component instanceof Container child) {
				T result = find(child, type);
				if (result != null) return result;
			}
		}
		return null;
	}

	public static AbstractButton button(Container root, String label) {
		for (Component component : root.getComponents()) {
			if (component instanceof AbstractButton button && label.equals(button.getText()))
				return button;
			if (component instanceof Container child) {
				AbstractButton result = button(child, label);
				if (result != null) return result;
			}
		}
		return null;
	}

	public static String text(Container root) {
		List<String> text = new ArrayList<>();
		for (Component component : root.getComponents()) {
			if (component instanceof JLabel label && label.getText() != null) text.add(label.getText());
			if (component instanceof AbstractButton button && button.getText() != null)
				text.add(button.getText());
			if (component instanceof JTextComponent value && value.getText() != null)
				text.add(value.getText());
			if (component instanceof Container child) text.add(text(child));
		}
		return String.join("\n", text);
	}

	/**
	 * Lays out and paints a component offscreen. The image is written only when the build passes
	 * {@code -Panvil.uiCaptures}, into {@code build/reports/ui-captures}.
	 */
	public static void capture(JComponent panel, String name, int width, int height)
			throws Exception {
		JRootPane root = new JRootPane();
		root.setContentPane(panel);
		root.setSize(width, height);
		// Measure viewport width, wrapped text height, and the resulting scrollbar width in order.
		for (int pass = 0; pass < 3; pass++) layout(root);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		try {
			root.printAll(graphics);
		} finally {
			graphics.dispose();
		}

		String captures = System.getProperty("anvil.uiCaptures");
		if (captures == null) return;

		Path directory = Files.createDirectories(Path.of(captures));
		String file = name.endsWith(".png") ? name : name + ".png";
		ImageIO.write(image, "png", directory.resolve(file).toFile());
	}

	private static void layout(Container component) {
		component.doLayout();
		for (Component child : component.getComponents())
			if (child instanceof Container container) layout(container);
	}
}
