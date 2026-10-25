package me.whereareiam.anvil.workbench.protocol.model.workspace;

import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;

/**
 * Requests about scenario runs and process workspaces on the workbench.
 *
 * @param <R> type of the workbench's answer
 */
public sealed interface WorkspaceProvisioningRequest<R> extends WorkbenchRequest<R>
		permits LayoutWorkspaces, PrepareWorkspace, FinishWorkspace, RecreateRunDirectory, FinishRunDirectory, DeleteRunDirectory {
}
