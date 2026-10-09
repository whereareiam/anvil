package me.whereareiam.anvil.tooling.api.model.action.binding;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

/**
 * Current action availability; the runner checks it again when invoking.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ActionAvailability {
	/**
	 * Whether the action can currently run.
	 */
	boolean enabled;

	/**
	 * Explanation when the action is unavailable.
	 */
	@Nullable
	String reason;
}
