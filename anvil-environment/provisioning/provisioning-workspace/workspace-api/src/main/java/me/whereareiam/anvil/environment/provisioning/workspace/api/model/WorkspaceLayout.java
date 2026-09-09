package me.whereareiam.anvil.environment.provisioning.workspace.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Map;

/**
 * Immutable directory assignments for one scenario run.
 * This value contains resolved paths only; creating or removing directories belongs to the provisioner.
 */
@Value
@Builder
public class WorkspaceLayout {
	/**
	 * Absolute directory containing the run's temporary files and fresh process workspaces.
	 */
	@NotNull Path runDirectory;

	/**
	 * Immutable mapping from declared process names to their absolute workspace directories.
	 * Persistent process directories are outside the run directory so run cleanup preserves them.
	 */
	@NotNull
	@Singular("processDirectory")
	Map<String, Path> processDirectories;

	/**
	 * Returns the workspace assigned to a declared server or proxy.
	 *
	 * @param name original process name from the scenario declaration
	 * @return absolute directory for the named process
	 * @throws IllegalArgumentException if the layout contains no process with this name
	 */
	public @NotNull Path processDirectory(@NotNull String name) {
		Path directory = processDirectories.get(name);
		if (directory == null)
			throw new IllegalArgumentException("No workspace directory is assigned to process: " + name);

		return directory;
	}
}
