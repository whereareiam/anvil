package me.whereareiam.anvil.workbench.protocol.model.artifact;

import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;

/**
 * Requests about artifacts the workbench downloads into its own cache.
 *
 * @param <R> type of the workbench's answer
 */
public sealed interface ArtifactRequest<R> extends WorkbenchRequest<R>
		permits ObtainArtifact, ReadArtifact {
}
