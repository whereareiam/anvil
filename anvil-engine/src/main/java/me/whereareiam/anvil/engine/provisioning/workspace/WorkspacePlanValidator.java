package me.whereareiam.anvil.engine.provisioning.workspace;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.workspace.AssetSource;
import me.whereareiam.anvil.api.model.workspace.WorkspaceAsset;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCleanup;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import me.whereareiam.anvil.api.type.CachePolicy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges provider cache defaults and validates workspace declarations before installation.
 */
@RequiredArgsConstructor
final class WorkspacePlanValidator {
	private final WorkspaceFiles files;

	WorkspacePlan validate(
			WorkspacePlan declared,
			List<WorkspaceCache> providerDefaults
	) {
		List<WorkspaceAsset> assets = List.copyOf(declared.getAssets());
		Map<String, WorkspaceCache> cachesByPath = new LinkedHashMap<>();
		for (WorkspaceCache cache : providerDefaults)
			cachesByPath.put(cachePath(cache), cache);
		for (WorkspaceCache cache : declared.getCaches())
			cachesByPath.put(cachePath(cache), cache);

		List<WorkspaceCache> allCaches = List.copyOf(cachesByPath.values());
		List<WorkspaceCache> caches = allCaches.stream()
				.filter(cache -> cache.getPolicy() != CachePolicy.DISABLED)
				.toList();
		List<WorkspaceCleanup> cleanups = List.copyOf(declared.getCleanups());
		validateAssets(assets);
		validateCaches(allCaches, cleanups);
		return WorkspacePlan.builder()
				.mode(declared.getMode())
				.assets(assets)
				.caches(caches)
				.cleanups(cleanups)
				.build();
	}

	private String cachePath(WorkspaceCache cache) {
		return files.resolveRelative(Path.of("/anvil-workspace"), cache.getPath(), "Cache path")
				.toString();
	}

	private void validateAssets(List<WorkspaceAsset> assets) {
		List<Path> targets = new ArrayList<>();
		for (WorkspaceAsset asset : assets) {
			if (asset.getGroup() == null || asset.getGroup().isBlank())
				throw new ScenarioValidationException("Workspace asset group must not be blank");
			Path target = files.resolveRelative(Path.of("/anvil-workspace"), asset.getTarget(), "Asset target");
			for (Path previous : targets)
				if (overlaps(previous, target))
					throw new ScenarioValidationException("Workspace asset targets overlap: " + previous + " and " + target);
			targets.add(target);
			AssetSource source = asset.getSource();
			if (source == null || (source.getPath() == null) == (source.getArtifactReference() == null))
				throw new ScenarioValidationException("Workspace asset must declare exactly one source: " + asset);
		}
	}

	private void validateCaches(
			List<WorkspaceCache> caches,
			List<WorkspaceCleanup> cleanups
	) {
		List<Path> cachePaths = new ArrayList<>();
		for (WorkspaceCache cache : caches) {
			if (cache.getGroup() == null || cache.getGroup().isBlank())
				throw new ScenarioValidationException("Workspace cache group must not be blank");
			Path path = files.resolveRelative(Path.of("/anvil-workspace"), cache.getPath(), "Cache path");
			for (Path previous : cachePaths)
				if (overlaps(previous, path))
					throw new ScenarioValidationException("Workspace cache paths overlap: " + previous + " and " + path);
			cachePaths.add(path);
		}
		for (WorkspaceCleanup cleanup : cleanups) {
			if (cleanup.getGroup() == null || cleanup.getGroup().isBlank())
				throw new ScenarioValidationException("Workspace cleanup group must not be blank");
			Path cleanupPath = files.resolveRelative(Path.of("/anvil-workspace"), cleanup.getPath(), "Cleanup path");
			for (Path cachePath : cachePaths)
				if (overlaps(cachePath, cleanupPath))
					throw new ScenarioValidationException("Workspace cleanup overlaps a cache path: " + cleanupPath);
		}
	}

	private static boolean overlaps(Path first, Path second) {
		return first.startsWith(second) || second.startsWith(first);
	}

}
