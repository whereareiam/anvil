package me.whereareiam.anvil.environment.provisioning.workspace.directory;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.WorkspaceMode;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Assigns confined scenario and process paths without creating their directories.
 */
public final class WorkspaceLayoutResolver {
	/**
	 * Assigns fresh run paths and stable persistent paths while rejecting process directory collisions.
	 * No directories are created by this operation.
	 *
	 * @param root configured workspace root
	 * @param scenario scenario and process declarations
	 * @return immutable run and process directory layout
	 */
	public @NotNull WorkspaceLayout resolve(@NotNull Path root, @NotNull AnvilScenario scenario) {
		String runId = Instant.now().toEpochMilli() + "-" + UUID.randomUUID().toString().substring(0, 8);
		Path scenarioDirectory = root.toAbsolutePath().normalize().resolve(safeName(scenario.getName()));
		Path runDirectory = scenarioDirectory.resolve(runId);
		var layout = WorkspaceLayout.builder().runDirectory(runDirectory);
		Map<Path, String> names = new HashMap<>();
		Stream.concat(scenario.getServers().stream(), scenario.getProxies().stream()).forEach(process -> {
			Path parent = process.getWorkspace().getMode() == WorkspaceMode.PERSISTENT
					? scenarioDirectory.resolve("persistent") : runDirectory;
			Path directory = parent.resolve(safeName(process.getName()));
			String previous = names.putIfAbsent(directory, process.getName());
			if (previous != null)
				throw new ScenarioValidationException("Processes '" + previous + "' and '" + process.getName()
						+ "' resolve to the same workspace directory: " + directory);
			layout.processDirectory(process.getName(), directory);
		});

		return layout.build();
	}

	private @NotNull String safeName(@NotNull String value) {
		String safe = value.replaceAll("[^A-Za-z0-9_.-]", "-");
		if (safe.isBlank() || safe.equals(".") || safe.equals(".."))
			throw new ScenarioValidationException("Unsafe scenario or process name: " + value);

		return safe;
	}
}
