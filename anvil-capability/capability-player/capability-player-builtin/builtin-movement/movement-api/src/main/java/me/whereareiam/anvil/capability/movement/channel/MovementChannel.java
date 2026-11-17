package me.whereareiam.anvil.capability.movement.channel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
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
	 * Places the player and turns its view; with physics on, the client goes on moving from there.
	 */
	public static final OperationDescriptor<Position, Void> MOVE = new OperationDescriptor<>(
			"movement.move", Position.class, Void.class
	);
	/**
	 * Turns the player's view without moving it.
	 */
	public static final OperationDescriptor<ViewRotation, Void> LOOK = new OperationDescriptor<>(
			"movement.look", ViewRotation.class, Void.class
	);
	/**
	 * Turns physics on or off, answering whether the change took effect or why the player's release cannot simulate
	 * the client.
	 */
	public static final OperationDescriptor<Boolean, PhysicsChange> PHYSICS = new OperationDescriptor<>(
			"movement.physics", Boolean.class, PhysicsChange.class
	);
	/**
	 * Sets the keys the client holds from its next tick on.
	 */
	public static final OperationDescriptor<MovementInput, Void> INPUT = new OperationDescriptor<>(
			"movement.input", MovementInput.class, Void.class
	);
	/**
	 * Reads the client's movement.
	 */
	public static final OperationDescriptor<Void, MovementState> STATE = new OperationDescriptor<>(
			"movement.state", Void.class, MovementState.class
	);
}
