package me.whereareiam.anvil.tooling.api.model.action.observation;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Identity and optional label for a contributed runtime observation.
 */
@Value
@Builder(toBuilder = true)
@Jacksonized
public class ObservationDefinition {
	/**
	 * Globally unique namespaced observation identifier.
	 */
	@NotNull
	String id;

	/**
	 * Optional label; clients fall back to the identifier.
	 */
	@Nullable
	String displayName;

}
