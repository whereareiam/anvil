package me.whereareiam.anvil.workbench.protocol.model.execution;

import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;

/**
 * Requests about execution sessions, prepared processes and running processes.
 *
 * @param <R> type of the workbench's answer
 */
public sealed interface ExecutionRequest<R> extends WorkbenchRequest<R>
		permits OpenSession, CloseSession, PrepareTarget, CloseTarget, StartProcess, CheckProcess, AwaitProcess, TerminateProcess, FreezeProcess, ThawProcess, ExecuteInProcess {
}
