package me.whereareiam.anvil.capability.movement;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * Typed movement operations shared by the host provider and the worker's movement binding.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MovementOperations {
	/**
	 * Sends an absolute position and view update.
	 */
	public static final OperationDescriptor<Position, Void> MOVE = new OperationDescriptor<>(
			"movement.move", Position.class, Void.class
	);
}
