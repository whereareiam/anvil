package me.whereareiam.anvil.integration.intellij.view.window.main.catalog;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.ui.UIUtil;

import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;

import me.whereareiam.anvil.integration.intellij.UiPlatformTestCase;
import me.whereareiam.anvil.integration.intellij.WindowTestSupport;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegration;
import me.whereareiam.anvil.integration.intellij.source.BuildIntegrations;
import me.whereareiam.anvil.integration.intellij.source.ProjectBuildIntegrations;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;

public class SourceSyncUiTest extends UiPlatformTestCase {
	@Override
	protected boolean isIconRequired() {
		return true;
	}

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		ServiceContainerUtil.replaceService(
				getProject(),
				BuildIntegrations.class,
				new ProjectBuildIntegrations(getProject()),
				getTestRootDisposable());

	}

	public void testOpeningWindowSyncsMissingModelAndLoadsAfterImport() throws Exception {
		SyncProvider provider = new SyncProvider();
		ScenarioCatalogPanel panel = panel(provider);
		await(() -> provider.syncs.get() == 1);
		assertEquals(1, provider.syncs.get());
		assertTrue(hasText(panel, "Syncing project"));
		assertEquals(0, provider.preparations.get());

		provider.discovery = importedProject();
		provider.changed();
		provider.completion.complete(null);
		provider.changed();
		await(() -> provider.preparations.get() == 1);
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(1, provider.preparations.get());
	}

	public void testOpeningWindowLoadsCachedModelWithoutSyncing() throws Exception {
		SyncProvider provider = new SyncProvider();
		provider.discovery = importedProject();
		ScenarioCatalogPanel panel = panel(provider);
		await(() -> provider.preparations.get() == 1);
		provider.changed();
		provider.changed();
		await(() -> hasText(panel, "Could not load scenarios"));
		assertEquals(0, provider.syncs.get());
		assertEquals(
				"A failed preparation must await an explicit retry", 1, provider.preparations.get());
	}

	public void testCachedModelWaitsForAlreadyRunningNativeImport() throws Exception {
		SyncProvider provider = new SyncProvider();
		provider.discovery = importedProject().toBuilder().activeSync(provider.completion).build();
		ScenarioCatalogPanel panel = panel(provider);
		await(() -> hasText(panel, "Syncing project"));
		assertTrue(hasText(panel, "Syncing project"));
		assertEquals(0, provider.preparations.get());
		provider.discovery = importedProject();
		provider.completion.complete(null);
		await(() -> provider.preparations.get() == 1);
		assertEquals(
				"Awaiting a captured native import must not request another sync",
				0,
				provider.syncs.get());
	}

	public void testSuccessfulImportWithoutAnvilDoesNotRepeatSync() throws Exception {
		SyncProvider provider = new SyncProvider();
		ScenarioCatalogPanel panel = panel(provider);
		await(() -> provider.syncs.get() == 1);
		provider.discovery =
				SourceListing.builder()
						.status(SourceListingStatus.NOT_CONFIGURED)
						.message("No Anvil sources are configured")
						.build();
		provider.completion.complete(null);
		await(() -> hasText(panel, "Set up Anvil"));
		provider.changed();
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(1, provider.syncs.get());
		assertEquals(0, provider.preparations.get());
	}

	public void testAlreadyImportedProjectWithoutAnvilWaitsForConfiguration() throws Exception {
		SyncProvider provider = new SyncProvider();
		provider.discovery =
				SourceListing.builder()
						.status(SourceListingStatus.NOT_CONFIGURED)
						.message("No Anvil sources are configured")
						.build();
		ScenarioCatalogPanel panel = panel(provider);
		await(() -> hasText(panel, "Set up Anvil"));
		assertEquals(0, provider.syncs.get());
		provider.discovery = importedProject();
		provider.changed();
		await(() -> provider.preparations.get() == 1);
		assertEquals(0, provider.syncs.get());
	}

	public void testUnlinkedProjectNeverRequestsSync() throws Exception {
		SyncProvider provider = new SyncProvider();
		provider.linked = false;
		ScenarioCatalogPanel panel = panel(provider);
		await(() -> hasText(panel, "Sync to discover scenarios"));
		assertEquals(0, provider.syncs.get());
		assertEquals(0, provider.preparations.get());
	}

	public void testSafeModeDoesNotExecuteCachedProjectAndTrustResumesLoading() throws Exception {
		SyncProvider provider = new SyncProvider();
		provider.discovery = importedProject();
		TrustedProjects.setProjectTrusted(getProject(), false);
		try {
			ScenarioCatalogPanel panel = panel(provider);
			await(() -> hasText(panel, "Project is in Safe Mode"));
			assertFalse(WindowTestSupport.actionEnabled(panel, "Sync project"));
			assertFalse(WindowTestSupport.actionEnabled(panel, "Load scenarios"));
			assertFalse(WindowTestSupport.find(panel, JComboBox.class).isEnabled());
			assertEquals(0, provider.preparations.get());
			assertEquals(0, provider.syncs.get());
			TrustedProjects.setProjectTrusted(getProject(), true);
			await(() -> provider.preparations.get() == 1);
		} finally {
			TrustedProjects.setProjectTrusted(getProject(), true);
		}
	}

	public void testFailedSyncOffersRetryWithoutPreparingStaleData() throws Exception {
		SyncProvider provider = new SyncProvider();
		ScenarioCatalogPanel panel = panel(provider);
		await(() -> provider.syncs.get() == 1);
		provider.discovery = importedProject();
		provider.completion.completeExceptionally(
				new IllegalStateException("Build configuration failed"));
		await(() -> button(panel, "Retry sync") != null);
		assertNotNull(button(panel, "View sync details"));
		assertTrue(hasText(panel, "Project sync needs attention"));
		provider.listeners.forEach(listener -> listener.accept(ProjectChange.IMPORT_FAILED));
		UIUtil.dispatchAllInvocationEvents();
		assertEquals(
				"A failed native sync must await an explicit retry", 1, provider.syncs.get());
		assertEquals(0, provider.preparations.get());
	}

	public void testSyncEmptyStateIsCenteredInDarkAndLightThemes() throws Exception {
		SyncProvider provider = new SyncProvider();
		ScenarioCatalogPanel panel = panel(provider);
		await(() -> hasText(panel, "Syncing project"));
		for (boolean dark : List.of(true, false)) {
			WindowTestSupport.useTheme(getTestRootDisposable(), dark);
			String theme = dark ? "dark" : "light";
			WindowTestSupport.capture(panel, "anvil-sync-" + theme + "-1000x560.png", 1000, 560);
			WindowTestSupport.capture(panel, "anvil-sync-" + theme + "-1200x400.png", 1200, 400);
			JLabel body = findExplanation(panel);
			assertNotNull(body);
			assertEquals(JLabel.CENTER, body.getHorizontalAlignment());
			assertFalse(body.isOpaque());
		}
	}

	public void testSuccessfulExternalReloadRecoversAfterFailedAutomaticSync() throws Exception {
		SyncProvider provider = new SyncProvider();
		ScenarioCatalogPanel panel = panel(provider);
		await(() -> provider.syncs.get() == 1);
		provider.completion.completeExceptionally(
				new IllegalStateException("Build configuration failed"));
		await(() -> hasText(panel, "Project sync needs attention"));
		provider.discovery = importedProject();
		provider.changed();
		await(() -> provider.preparations.get() == 1);
		assertEquals(1, provider.syncs.get());
	}

	private static JLabel findExplanation(Container root) {
		for (Component component : root.getComponents()) {
			if (component instanceof JLabel label
					&& label.getText() != null
					&& label.getText().startsWith("<html><div")) return label;
			if (component instanceof Container child) {
				JLabel found = findExplanation(child);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static SourceListing importedProject() {
		return SourceListing.builder()
				.status(SourceListingStatus.READY)
				.message("Project imported")
				.sources(
						List.of(
								ScenarioSource.builder()
										.id("fixture:sync")
										.integrationId("fixture-sync")
										.displayName("Example")
										.directory(Path.of("/example"))
										.build()))
				.build();
	}

	private ScenarioCatalogPanel panel(SyncProvider provider) {
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(provider), getTestRootDisposable());
		ScenarioCatalogPanel panel =
				new ScenarioCatalogPanel(
						getProject(),
						(source, scenario) -> fail("Catalog sync must not run an environment"),
						(source, scenario, process) -> fail("Catalog sync must not start a process"));
		Disposer.register(getTestRootDisposable(), panel);
		return panel;
	}

	private static void await(BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
			UIUtil.dispatchAllInvocationEvents();
			Thread.sleep(5);
		}
		assertTrue("Timed out waiting for the project UI state", condition.getAsBoolean());
	}

	private static AbstractButton button(Container root, String text) {
		for (Component component : root.getComponents()) {
			if (component instanceof AbstractButton button && text.equals(button.getText()))
				return button;
			if (component instanceof Container child) {
				AbstractButton found = button(child, text);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static boolean hasText(Container root, String text) {
		for (Component component : root.getComponents()) {
			if (component instanceof JLabel label && text.equals(label.getText())) return true;
			if (component instanceof Container child && hasText(child, text)) return true;
		}
		return false;
	}

	private static final class SyncProvider implements BuildIntegration {
		private final AtomicInteger syncs = new AtomicInteger();
		private final AtomicInteger preparations = new AtomicInteger();
		private final List<Consumer<ProjectChange>> listeners = new CopyOnWriteArrayList<>();
		private final CompletableFuture<Void> completion = new CompletableFuture<>();
		private boolean linked = true;
		private volatile SourceListing discovery =
				SourceListing.builder()
						.status(SourceListingStatus.NOT_IMPORTED)
						.message("Sync to import the Anvil model")
						.build();

		@Override
		public @NotNull String getId() {
			return "fixture-sync";
		}

		@Override
		public @NotNull SourceListing discover(@NotNull Project project) {
			return discovery;
		}

		@Override
		public boolean canSync(@NotNull Project project) {
			return linked;
		}

		@Override
		public @NotNull CompletableFuture<Void> sync(@NotNull Project project) {
			syncs.incrementAndGet();
			return completion;
		}

		@Override
		public void subscribe(
				@NotNull Project project,
				@NotNull Consumer<ProjectChange> listener,
				@NotNull Disposable owner) {
			listeners.add(listener);
			Disposer.register(owner, () -> listeners.remove(listener));
		}

		@Override
		public @NotNull ScenarioPreparation prepare(
				@NotNull Project project, @NotNull ScenarioSource selected) throws IOException {
			preparations.incrementAndGet();
			throw new IOException("Preparation reached after importing the model");
		}

		private void changed() {
			listeners.forEach(listener -> listener.accept(ProjectChange.IMPORTED));
		}
	}
}
