package me.whereareiam.anvil.tooling.api.model.action.definition;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import lombok.Singular;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Description shared by every target exposing the same action.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ActionDefinition {
	/**
	 * Globally unique namespaced action identifier.
	 */
	@NotNull
	String id;

	/**
	 * Optional label; clients fall back to the action identifier.
	 */
	@Nullable
	String displayName;

	/**
	 * Optional explanation of the action.
	 */
	@Nullable
	String description;

	/**
	 * Ordered input schema for generated forms and validation.
	 */
	@NotNull
	@Singular("input")
	List<ActionInput> inputs;
}
