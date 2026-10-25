package me.whereareiam.anvil.workbench.protocol.model;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.jetbrains.annotations.NotNull;

/**
 * One request an engine sends a workbench. Requests are grouped by subject, each subject a sealed interface whose
 * requests the workbench answers exhaustively; every request names the type of its answer.
 *
 * @param <R> type of the workbench's answer
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "request")
public interface WorkbenchRequest<R> {
	/**
	 * Returns the type the workbench answers this request with.
	 *
	 * @return answer type
	 */
	@NotNull Class<R> responseType();
}
