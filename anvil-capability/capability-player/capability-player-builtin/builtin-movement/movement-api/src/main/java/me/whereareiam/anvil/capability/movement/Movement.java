package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Controls where a simulated player is and which way it looks, either by sending positions the test chooses or by
 * running the vanilla client's physics.
 *
 * <p>Without client physics, which is the default, the player stays where the server placed it and moves only
 * through {@link #move(Position)}. With {@link #physics(boolean) physics} on, the player's client runs the vanilla
 * client's movement 20 times a second while it is connected and in a world: it falls, collides with the blocks it
 * knows, walks, sprints, sneaks, jumps, swims and climbs from the keys it {@link #input(MovementInput) holds} and
 * reports its movement to the server as the vanilla client of its release does.</p>
 *
 * <pre>{@code
 * Movement movement = player.capability(Movement.class);
 * movement.physics(true);
 * movement.input(MovementInput.builder().forward(true).build());
 * movement.awaitTicks(40);
 * Position reached = movement.position();
 * }</pre>
 */
public interface Movement extends PlayerCapability {
	/**
	 * Sends an absolute position and view update. With client physics on, the client is placed there with no
	 * velocity and its physics continues from that position.
	 *
	 * @param position new player position
	 */
	void move(@NotNull Position position);

	/**
	 * Turns client physics on or off. The setting belongs to the player: it may be changed before the player
	 * connects and stays across reconnects. The client starts moving once the server placed it on a connection and
	 * moves only while the chunk it is in is loaded, as the vanilla client does.
	 *
	 * @param enabled whether the client runs the vanilla client's physics
	 * @throws IllegalStateException when physics is turned on for a release Anvil has no block data for, such as a
	 * Minecraft version newer than its data source; the message names the reason
	 */
	void physics(boolean enabled);

	/**
	 * Tells whether client physics is on.
	 *
	 * @return whether the client runs the vanilla client's physics
	 */
	boolean physics();

	/**
	 * Holds movement keys from the client's next tick on, until other keys replace them. The keys move the player
	 * only with client physics on; {@link MovementInput#NONE} releases every key.
	 *
	 * @param input the keys to hold
	 */
	void input(@NotNull MovementInput input);

	/**
	 * Turns the player's view. With client physics on, the client reports the new direction with its next tick and
	 * walks the way it looks; without, the direction is sent at once with the player's position unchanged.
	 *
	 * @param yaw horizontal direction in degrees
	 * @param pitch vertical direction in degrees, from -90 looking up to 90 looking down
	 */
	void look(float yaw, float pitch);

	/**
	 * Returns where the client is: its position, view direction and whether it stands on the ground, as the server's
	 * latest placement and, with client physics, the client's own movement since left it.
	 *
	 * @return the client's position
	 * @throws IllegalStateException while the client does not know where it is, before the server placed it on the
	 * current connection
	 */
	@NotNull Position position();

	/**
	 * Returns the client's velocity in blocks per tick, which only client physics changes.
	 *
	 * @return the velocity; zero without client physics
	 */
	@NotNull Motion motion();

	/**
	 * Waits until the client has run a number of further physics ticks, with a timeout of five seconds plus twice
	 * the time the ticks take.
	 *
	 * @param ticks number of ticks, at least one
	 * @throws IllegalStateException when the ticks did not run in time, such as without client physics or while the
	 * player is in an unloaded chunk
	 */
	void awaitTicks(int ticks);

	/**
	 * Waits until the client has run a number of further physics ticks.
	 *
	 * @param ticks number of ticks, at least one
	 * @param timeout maximum positive wait
	 * @throws IllegalStateException when the ticks did not run in time, such as without client physics or while the
	 * player is in an unloaded chunk
	 */
	void awaitTicks(int ticks, @NotNull Duration timeout);
}
