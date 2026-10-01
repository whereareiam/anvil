package me.whereareiam.anvil.integration.intellij.gradle.resolver;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.gradle.ModuleDataRegistration;
import me.whereareiam.anvil.integration.intellij.gradle.model.ImportedModule;
import me.whereareiam.anvil.integration.intellij.gradle.model.ResolvedModule;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.model.source.SourceListing;
import me.whereareiam.anvil.integration.intellij.type.source.SourceListingStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleModuleDataKt;

/**
 * Resolves native imports into scenario-source identities and execution routes.
 * Each call reads its supplied imports without retaining state from previous resolutions.
 */
@RequiredArgsConstructor
public final class ScenarioSourceResolver {
	private final @NotNull String integrationId;

	/**
	 * Resolves available scenario sources and import diagnostics while preserving the observed sync.
	 *
	 * @param ideDirectory base used for stable relative identities, or null for absolute identities
	 * @param imports current native imports; a null entry represents an unavailable linked build
	 * @param sync native import completion currently observed by the caller
	 * @return immutable discovery snapshot for these imports
	 */
	public @NotNull SourceListing discover(
			@Nullable Path ideDirectory,
			@NotNull List<@Nullable ExternalProjectInfo> imports,
			@Nullable CompletableFuture<Void> sync
	) {
		Resolution resolution = resolveImports(ideDirectory, imports);
		return SourceListing.builder()
				.sources(resolution.modules().stream().map(ResolvedModule::getSource).toList())
				.status(status(resolution))
				.message(message(resolution))
				.activeSync(sync)
				.build();
	}

	/**
	 * Resolves an exact saved scenario-source identity to its current native execution route.
	 *
	 * @param ideDirectory base used for stable relative identities, or null for absolute identities
	 * @param imports current native imports; a null entry represents an unavailable linked build
	 * @param id saved scenario-source identity
	 * @return the selected scenario source and its current preparation route
	 * @throws IOException if the selected scenario source is absent from the supplied imports
	 */
	public @NotNull ResolvedModule resolve(
			@Nullable Path ideDirectory,
			@NotNull List<@Nullable ExternalProjectInfo> imports,
			@NotNull String id
	) throws IOException {
		Resolution resolution = resolveImports(ideDirectory, imports);
		for (ResolvedModule module : resolution.modules())
			if (module.getSource().getId().equals(id))
				return module;

		throw new IOException("The selected Anvil project is no longer in the imported project model.");
	}

	private @NotNull Resolution resolveImports(
			@Nullable Path ideDirectory,
			@NotNull List<@Nullable ExternalProjectInfo> imports
	) {
		Path ideRoot = ideDirectory == null ? null : ideDirectory.toAbsolutePath().normalize();
		Map<String, ResolvedModule> modules = new LinkedHashMap<>();

		ImportIssue issue = null;
		for (ExternalProjectInfo info : imports) {
			Resolution imported = resolveImport(ideRoot, info);
			issue = prioritize(issue, imported.issue());
			for (ResolvedModule module : imported.modules()) {
				modules.putIfAbsent(module.getSource().getId(), module);
			}
		}

		return new Resolution(List.copyOf(modules.values()), issue);
	}

	private @NotNull Resolution resolveImport(@Nullable Path ideRoot, @Nullable ExternalProjectInfo info) {
		if (info == null) return new Resolution(List.of(), ImportIssue.MISSING);

		long revision = info.getLastSuccessfulImportTimestamp();
		if (info.getLastImportTimestamp() != revision) {
			return new Resolution(List.of(), ImportIssue.FAILED);
		}

		var root = info.getExternalProjectStructure();
		if (revision <= 0 || root == null) return new Resolution(List.of(), ImportIssue.MISSING);
		if (root.isIgnored()) return new Resolution(List.of(), null);

		var nodes = ExternalSystemApiUtil.findAllRecursively(root, ModuleDataRegistration.KEY);
		if (nodes.isEmpty()) return new Resolution(List.of(), ImportIssue.MISSING);

		List<ResolvedModule> modules = new ArrayList<>();
		ImportIssue issue = null;
		for (var node : nodes) {
			if (isIgnored(node)) continue;
			ImportedModule model = node.getData();
			if (model.getSchemaVersion() != ImportedModule.SCHEMA_VERSION || model.isIncompatible()) {
				issue = ImportIssue.INCOMPATIBLE;
				continue;
			}

			if (!model.isEnabled()) continue;
			modules.add(resolveModule(ideRoot, root, node, revision));
		}

		return new Resolution(List.copyOf(modules), issue);
	}

	private @NotNull ResolvedModule resolveModule(
			@Nullable Path ideRoot,
			@NotNull DataNode<ProjectData> root,
			@NotNull DataNode<ImportedModule> node,
			long revision
	) {
		ImportedModule model = node.getData();
		String identity = nativeIdentity(node);
		String displayName = root.getData().getExternalName();
		if (!identity.equals(":")) {
			displayName += " / " + identity.substring(1)
					.replace(":", " / ");
		}

		Path buildRoot = Path.of(model.getBuildRootDirectory()).toAbsolutePath().normalize();
		ScenarioSource source = ScenarioSource.builder()
				.id(identifier(ideRoot, buildRoot, model.getProjectPath()))
				.displayName(displayName)
				.integrationId(integrationId)
				.directory(Path.of(model.getModuleDirectory()).toAbsolutePath().normalize())
				.importRevision(revision)
				.build();

		return ResolvedModule.builder()
				.source(source)
				.executionDirectory(Path.of(model.getExecutionDirectory()).toAbsolutePath().normalize())
				.preparationTaskPath(model.getPreparationTaskPath())
				.build();
	}

	private @NotNull String nativeIdentity(@NotNull DataNode<ImportedModule> node) {
		var module = node.getData(ProjectKeys.MODULE);
		String identity = module == null ? null : GradleModuleDataKt.getGradleIdentityPathOrNull(module);

		if (identity == null) {
			throw new IllegalStateException("Anvil's imported module " +
					"is missing its native project identity. Sync the project again.");
		}

		return identity;
	}

	private @NotNull String identifier(
			@Nullable Path ideRoot,
			@NotNull Path linkedRoot,
			@NotNull String modulePath
	) {
		String rootId = linkedRoot.toUri().toString();
		if (ideRoot != null && linkedRoot.startsWith(ideRoot)) {
			rootId = ideRoot.relativize(linkedRoot)
					.toString()
					.replace('\\', '/');
		}

		if (rootId.isEmpty()) rootId = ".";

		return integrationId + ":" + URLEncoder.encode(rootId, StandardCharsets.UTF_8)
				+ "#" + URLEncoder.encode(modulePath, StandardCharsets.UTF_8);
	}

	private boolean isIgnored(@NotNull DataNode<?> node) {
		for (DataNode<?> current = node; current != null; current = current.getParent())
			if (current.isIgnored()) return true;

		return false;
	}

	private @Nullable ImportIssue prioritize(@Nullable ImportIssue current, @Nullable ImportIssue candidate) {
		if (candidate == null) return current;
		if (current == null || candidate.compareTo(current) > 0) return candidate;

		return current;
	}

	private @NotNull SourceListingStatus status(@NotNull Resolution resolution) {
		if (resolution.issue() != null) return SourceListingStatus.NOT_IMPORTED;
		if (resolution.modules().isEmpty()) return SourceListingStatus.NOT_CONFIGURED;

		return SourceListingStatus.READY;
	}

	private @NotNull String message(@NotNull Resolution resolution) {
		if (resolution.issue() != null) return resolution.issue().message;
		if (resolution.modules().isEmpty())
			return "No scenario sources were detected. Apply me.whereareiam.anvil to a consumer module,"
					+ " then select Sync project.";

		return "Scenario source detected.";
	}

	/**
	 * Ordered by diagnostic priority, independently of linked-project traversal order.
	 */
	@RequiredArgsConstructor
	private enum ImportIssue {
		MISSING("Select Sync project to load scenario sources."),
		INCOMPATIBLE("The project tooling and IDE plugin are incompatible. Update them together,"
				+ " then select Sync project."),
		FAILED("Project sync failed. Select Sync project to try again.");

		private final String message;
	}

	private record Resolution(@NotNull List<ResolvedModule> modules, @Nullable ImportIssue issue) {}
}
