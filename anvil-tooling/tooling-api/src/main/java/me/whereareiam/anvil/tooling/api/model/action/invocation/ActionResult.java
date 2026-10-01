package me.whereareiam.anvil.tooling.api.model.action.invocation;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Portable action outcome displayed as a message and optional table.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ActionResult {
	/**
	 * Whether the action reports successful completion.
	 */
	@Builder.Default
	boolean successful = true;

	/**
	 * Optional user-facing outcome.
	 */
	@Nullable
	String message;

	/**
	 * Ordered column labels for tabular results.
	 */
	@NotNull
	@Singular("column")
	List<String> columns;

	/**
	 * Table rows; each row must match the declared column count.
	 */
	@NotNull
	@Singular("row")
	List<List<String>> rows;
}
