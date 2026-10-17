package me.whereareiam.anvil.capability.movement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.movement.model.Position;

/**
 * Typed movement operations shared by the host provider and the worker's movement binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MovementOperations {
	/**
	 * Sends an absolute position and view update.
	 */
	public static final ChannelOperation<Position, Void> MOVE = new ChannelOperation<>("movement.move", Position.class, Void.class);
}
