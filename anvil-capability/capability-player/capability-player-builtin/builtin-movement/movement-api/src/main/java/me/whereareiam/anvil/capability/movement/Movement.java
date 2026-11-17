package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.movement.model.Position;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Moves a simulated player and turns its view, as a test places it or as the vanilla client's own physics moves it.
 * Without physics, which is how a player starts, the client goes exactly where the test moves it and stays there. With
 * {@link #physics() client physics} on, the client moves as a vanilla client of its Minecraft version does: it falls,
 * walks with the keys the test holds, collides with the blocks its world knows and follows what the server says about
 * its abilities, attributes, effects, game mode and death.
 *
 * <pre>{@code
 * Movement movement = alice.capability(Movement.class);
 * movement.move(Position.builder().x(0.5).y(64).z(0.5).onGround(true).build());
 *
 * movement.physics().enable();
 * movement.physics().input(MovementInput.builder().forward(true).build());
 * movement.physics().awaitTicks(20);
 * }</pre>
 */
public interface Movement extends PlayerCapability {
	/**
	 * Places the player and turns its view: the client sends the position to the server and, with physics on, goes on
	 * moving from there.
	 *
	 * @param position the player's new position and view direction
	 */
	void move(@NotNull Position position);

	/**
	 * Turns the player's view without moving it.
	 *
	 * @param yaw the horizontal view direction in degrees
	 * @param pitch the vertical view direction in degrees, from -90 looking up to 90 looking down
	 */
	void look(float yaw, float pitch);

	/**
	 * Returns where the client is now, without waiting.
	 *
	 * @return the client's position, or empty before the server first placed it on its current connection
	 */
	@NotNull Optional<Position> position();

	/**
	 * Returns the player's client physics, which moves it as the vanilla client does.
	 *
	 * @return the client physics
	 */
	@NotNull ClientPhysics physics();
}
