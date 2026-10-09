package me.whereareiam.anvil.integration.intellij.source;

import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ExtensionTestUtil;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import me.whereareiam.anvil.integration.intellij.EnginePlatformTestCase;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioPreparation;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.type.source.ProjectChange;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;

public class ProjectBuildIntegrationsPlatformTest extends EnginePlatformTestCase {
	public void testSafeModeBlocksNativeSyncAndDirectPreparation() {
		ScenarioSource source = source("one", "Plugin");
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS,
				List.of(provider(List.of(source))),
				getTestRootDisposable());
		var service = getProject().getService(BuildIntegrations.class);
		TrustedProjects.setProjectTrusted(getProject(), false);
		try {
			assertEquals(source, service.resolve("one"));
			assertTrue(service.sync().isCompletedExceptionally());
			assertThrows(IOException.class, () -> service.prepare(source));
		} finally {
			TrustedProjects.setProjectTrusted(getProject(), true);
		}
	}

	public void testBlankSelectionUsesSoleProjectAndSavedMissingSelectionNeverFallsBack() {
		ScenarioSource source = source("one", "Plugin");
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS,
				List.of(provider(List.of(source))),
				getTestRootDisposable());
		var service = getProject().getService(BuildIntegrations.class);
		assertEquals(source, service.resolve(""));
		assertEquals(source, service.resolve("one"));
		assertThrows(IllegalStateException.class, () -> service.resolve("previous-checkout"));
	}

	public void testMultipleProjectsRequireExplicitSelectionAndPreparationUsesSelectedProvider()
			throws Exception {
		ScenarioSource first = source("one", "First");
		ScenarioSource second = source("two", "Second");
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS,
				List.of(provider(List.of(first, second))),
				getTestRootDisposable());
		var service = getProject().getService(BuildIntegrations.class);
		assertEquals(SourceListingStatus.READY, service.discover().getStatus());
		assertThrows(IllegalStateException.class, () -> service.resolve(""));
		assertEquals(second, service.resolve("two"));
		assertEquals(List.of("prepare", "two"), service.prepare(second).getCommand());
	}

	public void testNoInstalledProviderGivesUnsupportedStateWithoutLoadingOptionalGradleClasses() {
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS, List.of(), getTestRootDisposable());
		var service = getProject().getService(BuildIntegrations.class);
		assertEquals(SourceListingStatus.UNSUPPORTED, service.discover().getStatus());
		assertTrue(service.discover().getSources().isEmpty());
	}

	public void testPartialDiscoveryKeepsValidProjectsAndActionableFailedSyncMessage() {
		ScenarioSource available = source("available", "Available");
		String message = "Project sync failed. Select Sync project to try again.";
		ExtensionTestUtil.maskExtensions(
				ProjectBuildIntegrations.BUILD_INTEGRATIONS,
				List.of(provider(List.of(available), SourceListingStatus.NOT_IMPORTED, message)),
				getTestRootDisposable());
		var service = getProject().getService(BuildIntegrations.class);

		assertEquals(SourceListingStatus.READY, service.discover().getStatus());
		assertEquals(message, service.discover().getMessage());
		assertEquals(available, service.resolve("available"));
	}

	private static ScenarioSource source(String id, String name) {
		return ScenarioSource.builder()
				.id(id)
				.displayName(name)
				.integrationId("test")
				.directory(Path.of("/project", id))
				.build();
	}

	private static BuildIntegration provider(List<ScenarioSource> projects) {
		return provider(projects, SourceListingStatus.READY, "Ready");
	}

	private static BuildIntegration provider(
			List<ScenarioSource> projects, SourceListingStatus status, String message) {
		return new BuildIntegration() {
			@Override
			public @NotNull String getId() {
				return "test";
			}

			@Override
			public @NotNull SourceListing discover(@NotNull Project project) {
				return SourceListing.builder()
						.sources(projects)
						.status(status)
						.message(message)
						.build();
			}

			@Override
			public boolean canSync(@NotNull Project project) {
				return false;
			}

			@Override
			public @NotNull CompletableFuture<Void> sync(@NotNull Project project) {
				return CompletableFuture.failedFuture(
						new UnsupportedOperationException("This fixture does not sync projects."));
			}

			@Override
			public void subscribe(
					@NotNull Project project,
					@NotNull Consumer<ProjectChange> listener,
					@NotNull Disposable owner) {}

			@Override
			public @NotNull ScenarioPreparation prepare(
					@NotNull Project project, @NotNull ScenarioSource selected) {
				return ScenarioPreparation.builder()
						.command(List.of("prepare", selected.getId()))
						.workingDirectory(selected.getDirectory())
						.manifestPath(selected.getDirectory().resolve("tooling.json"))
						.build();
			}
		};
	}
}
