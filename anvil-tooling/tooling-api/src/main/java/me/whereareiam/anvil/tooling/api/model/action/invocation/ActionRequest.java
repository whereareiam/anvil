package me.whereareiam.anvil.tooling.api.model.action.invocation;

import java.util.Map;
import lombok.Builder;
import lombok.Value;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionTarget;
import org.jetbrains.annotations.NotNull;

/**
 * Explicit action invocation against one environment and target.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ActionRequest {
	/**
	 * Environment identity; stale requests are rejected.
	 */
	@NotNull
	String sessionId;

	/**
	 * Registered action identifier.
	 */
	@NotNull
	String actionId;

	/**
	 * Target selected by the caller.
	 */
	@NotNull
	ActionTarget target;

	/**
	 * Scalar argument values keyed by declared input name.
	 */
	@NotNull
	@Singular("argument")
	Map<String, String> arguments;
}
