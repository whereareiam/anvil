package me.whereareiam.anvil.workbench.protocol.model.launch;

import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;

/**
 * Requests about JVMs the workbench starts outside managed execution, such as player workers.
 *
 * @param <R> type of the workbench's answer
 */
public sealed interface LaunchRequest<R> extends WorkbenchRequest<R>
		permits Launch, AwaitLaunched, DestroyLaunched {
}
