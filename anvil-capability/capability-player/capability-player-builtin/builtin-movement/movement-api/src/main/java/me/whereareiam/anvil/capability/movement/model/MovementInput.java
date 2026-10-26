package me.whereareiam.anvil.capability.movement.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The movement keys a simulated player holds, as the vanilla client reads them from the keyboard each tick. Client
 * physics turns them into movement: forward and backward, strafing, jumping, sneaking and sprinting.
 *
 * <pre>{@code
 * movement.input(MovementInput.builder().forward(true).sprint(true).build());
 * }</pre>
 */
@Value
@Builder
public class MovementInput {
	/**
	 * No key held.
	 */
	public static final @NotNull MovementInput NONE = MovementInput.builder().build();

	/**
	 * Whether the key that walks forward is held.
	 */
	boolean forward;
	/**
	 * Whether the key that walks backward is held.
	 */
	boolean backward;
	/**
	 * Whether the key that strafes left is held.
	 */
	boolean left;
	/**
	 * Whether the key that strafes right is held.
	 */
	boolean right;
	/**
	 * Whether the jump key is held; the player jumps whenever it stands on the ground and its jump delay has passed.
	 */
	boolean jump;
	/**
	 * Whether the sneak key is held.
	 */
	boolean sneak;
	/**
	 * Whether the sprint key is held; the player sprints while it walks forward and can sprint.
	 */
	boolean sprint;
}
