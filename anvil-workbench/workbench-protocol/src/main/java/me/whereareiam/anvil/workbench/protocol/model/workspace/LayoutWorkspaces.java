package me.whereareiam.anvil.workbench.protocol.model.workspace;

import lombok.Value;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.environment.provisioning.workspace.api.model.WorkspaceLayout;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Lays out a scenario run below a root.
 */
@Value
public class LayoutWorkspaces implements WorkspaceProvisioningRequest<WorkspaceLayout> {
	/**
	 * Work root on the workbench.
	 */
	@NotNull Path root;

	/**
	 * The scenario.
	 */
	@NotNull AnvilScenario scenario;

	@Override
	public @NotNull Class<WorkspaceLayout> responseType() {
		return WorkspaceLayout.class;
	}
}
