package me.whereareiam.anvil.tooling.extension.api.action.scoped.player;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.extension.api.action.ToolingAction;
import org.jetbrains.annotations.NotNull;

/**
 * An action evaluated against a current player target.
 */
public abstract class PlayerAction extends ToolingAction<SimulatedPlayer> {
	/**
	 * Declares this player contribution.
	 *
	 * @param definition portable identity and presentation
	 */
	protected PlayerAction(@NotNull ActionDefinition definition) {
		super(definition);
	}
}
