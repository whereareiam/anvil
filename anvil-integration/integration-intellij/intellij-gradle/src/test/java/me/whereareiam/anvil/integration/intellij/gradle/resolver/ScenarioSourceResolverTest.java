package me.whereareiam.anvil.integration.intellij.gradle.resolver;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import me.whereareiam.anvil.integration.intellij.gradle.ModuleDataRegistration;
import me.whereareiam.anvil.integration.intellij.gradle.model.ImportedModule;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleModuleDataKt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioSourceResolverTest {
	private final ScenarioSourceResolver resolver = new ScenarioSourceResolver("gradle");
	@TempDir Path directory;

	@Test
	void retainsStableIdentitiesAndFirstImportedRoute() throws Exception {
		var root = root("Consumer");
		Path included = directory.resolve("included build");
		Path execution = directory.resolve("execution root");
		var payload = module(included).executionDirectory(execution.toString()).build();
		addModule(root, payload, ":included:plugin");
		List<ExternalProjectInfo> imports = List.of(imported(root, 100, 100), imported(root, 200, 200));
		CompletableFuture<Void> sync = new CompletableFuture<>();

		var discovery = resolver.discover(directory, imports, sync);
		assertEquals(SourceListingStatus.READY, discovery.getStatus());
		assertSame(sync, discovery.getActiveSync());
		assertEquals(1, discovery.getSources().size());
		var selected = discovery.getSources().getFirst();
		assertEquals("gradle:included+build#%3Aplugin", selected.getId());
		assertEquals("Consumer / included / plugin", selected.getDisplayName());
		assertEquals(included.resolve("plugin"), selected.getDirectory());
		assertEquals(100, selected.getImportRevision());

		var resolved = resolver.resolve(directory, imports, selected.getId());
		assertEquals(selected, resolved.getSource());
		assertEquals(execution, resolved.getExecutionDirectory());
		assertEquals(":plugin:anvilTooling", resolved.getPreparationTaskPath());
		assertThrows(IOException.class, () -> resolver.resolve(directory, imports, "missing-module"));
	}

	@Test
	void reportsFailedImportsFirstRegardlessOfTraversalOrder() {
		var healthy = root("Consumer");
		addModule(healthy, module(directory).build(), ":plugin");
		var incompatible = root("Old tooling");
		addModule(incompatible, module(directory).schemaVersion(0).build(), ":plugin");

		for (boolean failedFirst : new boolean[] {true, false}) {
			List<ExternalProjectInfo> imports = new ArrayList<>();
			if (failedFirst) imports.add(imported(healthy, 100, 200));
			imports.add(null);
			imports.add(imported(healthy, 100, 100));
			imports.add(imported(incompatible, 100, 100));
			if (!failedFirst) imports.add(imported(healthy, 100, 200));

			var discovery = resolver.discover(directory, imports, null);
			assertEquals(SourceListingStatus.NOT_IMPORTED, discovery.getStatus());
			assertEquals("Project sync failed. Select Sync project to try again.", discovery.getMessage());
			assertEquals(1, discovery.getSources().size());
		}
	}

	@Test
	void requiresImportForMissingOrIncompatibleModels() {
		var missing = resolver.discover(directory, List.of(imported(root("Consumer"), 100, 100)), null);
		assertEquals(SourceListingStatus.NOT_IMPORTED, missing.getStatus());

		var root = root("Consumer");
		addModule(root, module(directory).incompatible(true).enabled(false).build(), null);
		var incompatible = resolver.discover(directory, List.of(imported(root, 100, 100)), null);
		assertTrue(incompatible.getMessage().contains("incompatible"));
		assertTrue(incompatible.getSources().isEmpty());
	}

	@Test
	void ignoresDisabledModulesAndIgnoredParentsBeforeReadingIdentity() {
		var root = root("Consumer");
		addModule(root, module(directory).enabled(false).build(), null);
		var ignored = addModule(root, module(directory).schemaVersion(0).build(), null);
		ignored.getParent().setIgnored(true);
		List<ExternalProjectInfo> imports = new ArrayList<>();
		imports.add(imported(root, 100, 100));

		assertEquals(SourceListingStatus.NOT_CONFIGURED, resolver.discover(directory, imports, null).getStatus());
		assertTrue(resolver.discover(directory, imports, null).getSources().isEmpty());
	}

	@Test
	void refusesEnabledModulesWithoutNativeIdentity() {
		var root = root("Consumer");
		addModule(root, module(directory).build(), null);

		var failure = assertThrows(IllegalStateException.class,
				() -> resolver.discover(directory, List.of(imported(root, 100, 100)), null));
		assertTrue(failure.getMessage().contains("missing its native project identity"));
	}

	@Test
	void doesNotRetainModulesOrProblemsBetweenResolutions() throws IOException {
		var root = root("Consumer");
		addModule(root, module(directory).build(), ":plugin");
		var failed = resolver.discover(directory, List.of(imported(root, 100, 200)), null);
		assertEquals(SourceListingStatus.NOT_IMPORTED, failed.getStatus());

		List<ExternalProjectInfo> healthy = List.of(imported(root, 200, 200));
		var discovery = resolver.discover(directory, healthy, null);
		assertEquals(SourceListingStatus.READY, discovery.getStatus());
		var selected = discovery.getSources().getFirst();
		assertEquals(200, resolver.resolve(directory, healthy, selected.getId()).getSource().getImportRevision());

		var empty = resolver.discover(directory, List.of(), null);
		assertTrue(empty.getSources().isEmpty());
		assertEquals(SourceListingStatus.NOT_CONFIGURED, empty.getStatus());
		assertThrows(IOException.class, () -> resolver.resolve(directory, List.of(), selected.getId()));
	}

	private DataNode<ProjectData> root(String name) {
		var project = new ProjectData(GradleConstants.SYSTEM_ID, name, directory.toString(), directory.toString());
		return new DataNode<>(ProjectKeys.PROJECT, project, null);
	}

	private ImportedModule.ImportedModuleBuilder module(Path buildRoot) {
		return ImportedModule.builder()
				.enabled(true)
				.moduleDirectory(buildRoot.resolve("plugin").toString())
				.buildRootDirectory(buildRoot.toString())
				.projectPath(":plugin")
				.executionDirectory(buildRoot.toString())
				.preparationTaskPath(":plugin:anvilTooling");
	}

	private DataNode<ImportedModule> addModule(
			DataNode<ProjectData> root,
			ImportedModule model,
			@Nullable String identity
	) {
		var module = new ModuleData(
				"plugin",
				GradleConstants.SYSTEM_ID,
				"JAVA_MODULE",
				"plugin",
				directory.toString(),
				model.getModuleDirectory()
		);
		if (identity != null) GradleModuleDataKt.setGradleIdentityPath(module, identity);
		return root.createChild(ProjectKeys.MODULE, module).createChild(ModuleDataRegistration.KEY, model);
	}

	private ExternalProjectInfo imported(DataNode<ProjectData> root, long successful, long latest) {
		return new Import(directory.toString(), root, successful, latest);
	}

	// IntelliJ's own InternalExternalProjectInfo reads the build number from the running application,
	// which this plain unit test does not start.
	private record Import(
			String externalProjectPath,
			DataNode<ProjectData> externalProjectStructure,
			long lastSuccessfulImportTimestamp,
			long lastImportTimestamp
	) implements ExternalProjectInfo {
		@Override
		public @NotNull ProjectSystemId getProjectSystemId() {
			return GradleConstants.SYSTEM_ID;
		}

		@Override
		public @NotNull String getExternalProjectPath() {
			return externalProjectPath;
		}

		@Override
		public DataNode<ProjectData> getExternalProjectStructure() {
			return externalProjectStructure;
		}

		@Override
		public long getLastSuccessfulImportTimestamp() {
			return lastSuccessfulImportTimestamp;
		}

		@Override
		public long getLastImportTimestamp() {
			return lastImportTimestamp;
		}

		@Override
		public String getBuildNumber() {
			return "test";
		}

		@Override
		public @NotNull ExternalProjectInfo copy() {
			return this;
		}
	}
}
