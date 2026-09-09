package me.whereareiam.anvil.capability.movement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.movement.model.Position;

/**
 * Typed movement operations implemented by native capability bindings.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MovementOperations {
	/**
	 * Executes the move operation with a registered request schema.
	 */
	public static final ChannelOperation<Position, Void> MOVE = new ChannelOperation<>("movement.move", Position.class, Void.class);
}
