package me.whereareiam.anvil.capability.movement.channel;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.Position;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The movement of a player's client as its worker reports it on a read: whether physics is on, why it turned itself
 * off, how many ticks it has run, and where the client is and how fast it moves.
 */
@Value
@Builder
public class MovementState {
	/**
	 * Whether physics is on.
	 */
	boolean physics;
	/**
	 * Why physics turned itself off after a tick failed, or {@code null} while it is on or the test turned it off.
	 */
	@Nullable String failure;
	/**
	 * The number of physics ticks the client has run since the player was created, across its connections.
	 */
	long ticks;
	/**
	 * Where the client is, or {@code null} before the server first placed it on its current connection.
	 */
	@Nullable Position position;
	/**
	 * The client's velocity; zero while physics is off.
	 */
	@NotNull Motion motion;
}
