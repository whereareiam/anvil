package me.whereareiam.anvil.api.model.player;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;

/**
 * Immutable immediate snapshot of one simulated player's core lifecycle state.
 *
	 * <p>Capability-specific state is exposed by each capability instead of coupling this core model to
	 * every installed capability.</p>
 */
@Value
@Accessors(fluent = true)
@Builder(toBuilder = true)
public class PlayerState {
	boolean destroyed;
}
