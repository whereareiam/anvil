package me.whereareiam.anvil.workbench.protocol.model.java;

import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;

/**
 * Requests about the workbench's Java installations.
 *
 * @param <R> type of the workbench's answer
 */
public sealed interface JavaRequest<R> extends WorkbenchRequest<R>
		permits ResolveJava, InspectJavaSource, InspectJava, ValidateJava {
}
