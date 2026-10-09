package me.whereareiam.anvil.tooling.extension.api.observation.scoped.player;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.tooling.api.model.action.observation.ObservationDefinition;
import me.whereareiam.anvil.tooling.extension.api.observation.ToolingObservation;
import org.jetbrains.annotations.NotNull;

/**
 * An observation evaluated against a current player target.
 */
public abstract class PlayerObservation extends ToolingObservation<SimulatedPlayer> {
	/**
	 * Declares this player contribution.
	 *
	 * @param definition portable identity and presentation
	 */
	protected PlayerObservation(@NotNull ObservationDefinition definition) {
		super(definition);
	}
}
