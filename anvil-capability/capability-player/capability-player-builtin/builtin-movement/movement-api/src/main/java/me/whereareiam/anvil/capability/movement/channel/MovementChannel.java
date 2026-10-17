package me.whereareiam.anvil.capability.movement.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;

/**
 * The channel contract between the host movement capability and its worker extensions: the capability ID both
 * register under and the operation the host requests.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MovementChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.movement";
	/**
	 * Sends an absolute position and view update.
	 */
	public static final OperationDescriptor<Position, Void> MOVE = new OperationDescriptor<>(
			"movement.move", Position.class, Void.class
	);
}
