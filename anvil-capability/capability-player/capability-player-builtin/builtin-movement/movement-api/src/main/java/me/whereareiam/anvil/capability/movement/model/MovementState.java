package me.whereareiam.anvil.capability.movement.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The movement of a player's client as its worker reports it on a read: whether client physics is on, how many
 * physics ticks it has run, and where the client is and how fast it moves.
 */
@Value
@Builder(toBuilder = true)
public class MovementState {
	/**
	 * Whether client physics is on.
	 */
	boolean physics;
	/**
	 * Why the client cannot run physics, such as a release Anvil ships no block data for, or {@code null} when it
	 * can.
	 */
	@Nullable String physicsUnavailable;
	/**
	 * The number of physics ticks the client has run since the player was created, across its connections.
	 */
	long ticks;
	/**
	 * The client's position, rotation and ground state, or {@code null} while the client does not know where it is,
	 * before the server first placed it on the current connection.
	 */
	@Nullable Position position;
	/**
	 * The client's velocity; zero without client physics.
	 */
	@NotNull Motion motion;
}
