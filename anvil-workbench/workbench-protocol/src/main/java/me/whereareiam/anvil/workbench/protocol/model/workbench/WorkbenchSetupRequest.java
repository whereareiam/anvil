package me.whereareiam.anvil.workbench.protocol.model.workbench;

import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;

/**
 * Requests about the workbench itself: greeting it, its facts, and services of the engine it makes reachable.
 *
 * @param <R> type of the workbench's answer
 */
public sealed interface WorkbenchSetupRequest<R> extends WorkbenchRequest<R>
		permits Hello, ReadFacts, Expose {
}
