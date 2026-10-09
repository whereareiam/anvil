package me.whereareiam.anvil.tooling.api.model.action.binding;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.tooling.api.type.action.ActionTargetType;
import org.jetbrains.annotations.NotNull;

/**
 * Stable runtime target identity within one environment.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ActionTarget {
	/**
	 * Target kind.
	 */
	@NotNull
	ActionTargetType type;

	/**
	 * Scenario, process, or player name within its owning environment.
	 */
	@NotNull
	String name;
}
