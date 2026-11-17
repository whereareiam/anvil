package me.whereareiam.anvil.capability.movement;

import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;

/**
 * The vanilla client's physics for one simulated player, ticking twenty times a second while it is on: it applies the
 * keys the test holds, gravity, friction, fluids and the blocks the client's world knows, collides with them, and
 * sends the movement packets a vanilla client of the player's version sends. It follows what the server tells the
 * client about its abilities and flying, its attributes such as movement speed and jump strength, its effects such as
 * speed, jump boost, levitation and slow falling, its game mode, and its death, after which it holds no keys until the
 * player respawns.
 *
 * <p>A failing tick turns physics off; {@link #failure()} then tells why.</p>
 *
 * <pre>{@code
 * ClientPhysics physics = alice.capability(Movement.class).physics();
 * physics.enable();
 * physics.input(MovementInput.builder().forward(true).sprint(true).build());
 * physics.awaitTicks(40);
 * assertTrue(physics.motion().getX() != 0 || physics.motion().getZ() != 0);
 * }</pre>
 */
public interface ClientPhysics {
	/**
	 * Turns physics on, from the client's current position. It does nothing when physics is already on.
	 *
	 * @throws CapabilityUnavailableException when the player's release cannot simulate the client, such as one Anvil
	 * ships no block data for, with the reason
	 */
	void enable();

	/**
	 * Turns physics off: the client stays where it is and sends no more movement of its own.
	 */
	void disable();

	/**
	 * Tells whether physics is on.
	 *
	 * @return whether it ticks
	 */
	boolean isEnabled();

	/**
	 * Returns why physics turned itself off, after a tick failed.
	 *
	 * @return the failure, or empty while physics is on or was turned off by the test
	 */
	@NotNull Optional<String> failure();

	/**
	 * Sets the keys the client holds from its next tick on, until the next input.
	 *
	 * @param input the held keys
	 */
	void input(@NotNull MovementInput input);

	/**
	 * Returns the client's velocity now, without waiting.
	 *
	 * @return the velocity in blocks per tick; zero while physics is off
	 */
	@NotNull Motion motion();

	/**
	 * Waits until physics has run a number of ticks from now, within {@link SimulatedPlayer#DEFAULT_TIMEOUT}.
	 *
	 * @param ticks how many ticks to wait for
	 * @throws IllegalStateException when physics is off or turns off while waiting, or the ticks do not pass in time
	 */
	default void awaitTicks(int ticks) {
		awaitTicks(ticks, SimulatedPlayer.DEFAULT_TIMEOUT);
	}

	/**
	 * Waits until physics has run a number of ticks from now.
	 *
	 * @param ticks how many ticks to wait for
	 * @param timeout how long to wait
	 * @throws IllegalStateException when physics is off or turns off while waiting, or the ticks do not pass in time
	 */
	void awaitTicks(int ticks, @NotNull Duration timeout);
}
