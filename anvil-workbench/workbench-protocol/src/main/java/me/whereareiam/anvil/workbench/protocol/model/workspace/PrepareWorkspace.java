package me.whereareiam.anvil.workbench.protocol.model.workspace;

import lombok.Value;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.workspace.WorkspaceCache;
import me.whereareiam.anvil.api.model.workspace.WorkspacePlan;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.List;

/**
 * Prepares one process workspace; text assets arrive filled in.
 */
@Value
public class PrepareWorkspace implements WorkspaceProvisioningRequest<PreparedWorkspaceHandle> {
	/**
	 * Work root on the workbench.
	 */
	@NotNull Path root;

	/**
	 * The process's directory.
	 */
	@NotNull Path directory;

	/**
	 * The process's declaration.
	 */
	@NotNull MinecraftProcess process;

	/**
	 * Its workspace plan.
	 */
	@NotNull WorkspacePlan plan;

	/**
	 * Caches its platform provider adds.
	 */
	@NotNull List<WorkspaceCache> providerDefaults;

	@Override
	public @NotNull Class<PreparedWorkspaceHandle> responseType() {
		return PreparedWorkspaceHandle.class;
	}
}
