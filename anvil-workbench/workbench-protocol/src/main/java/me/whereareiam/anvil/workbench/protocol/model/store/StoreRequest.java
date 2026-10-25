package me.whereareiam.anvil.workbench.protocol.model.store;

import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;

/**
 * Requests about the files of the engine's machine the workbench keeps by their content.
 *
 * @param <R> type of the workbench's answer
 */
public sealed interface StoreRequest<R> extends WorkbenchRequest<R>
		permits FindStored {
}
