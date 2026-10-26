package me.whereareiam.anvil.capability.movement.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.MovementState;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;

/**
 * The channel contract between the host movement capability and its worker extensions: the capability ID both
 * register under and the operations the host requests.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MovementChannel {
	/**
	 * Capability ID of the host provider and of every edition's worker extension.
	 */
	public static final String ID = "me.whereareiam.anvil.movement";
	/**
	 * Sends an absolute position and view update; with client physics on, places the client there.
	 */
	public static final OperationDescriptor<Position, Void> MOVE = new OperationDescriptor<>(
			"movement.move", Position.class, Void.class
	);
	/**
	 * Turns client physics on or off; turning it on fails when the player's release cannot simulate it.
	 */
	public static final OperationDescriptor<Boolean, Void> PHYSICS = new OperationDescriptor<>(
			"movement.physics", Boolean.class, Void.class
	);
	/**
	 * Sets the movement keys the client holds from its next tick on.
	 */
	public static final OperationDescriptor<MovementInput, Void> INPUT = new OperationDescriptor<>(
			"movement.input", MovementInput.class, Void.class
	);
	/**
	 * Turns the client's view.
	 */
	public static final OperationDescriptor<ViewRotation, Void> LOOK = new OperationDescriptor<>(
			"movement.look", ViewRotation.class, Void.class
	);
	/**
	 * Reads the client's movement: physics, ticks, position and velocity.
	 */
	public static final OperationDescriptor<Void, MovementState> STATE = new OperationDescriptor<>(
			"movement.state", Void.class, MovementState.class
	);
}
