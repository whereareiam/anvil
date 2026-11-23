package me.whereareiam.anvil.capability.movement.java.physics.player;

import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.edition.java.physics.living.Jumping;
import org.jetbrains.annotations.NotNull;

/**
 * The flight of a player that may fly, as {@code LocalPlayer.aiStep} handles it: toggled by pressing jump twice within
 * seven ticks, which jumps off the ground when it starts there, forced on for a spectator, steered up and down with
 * jump and sneak, and ended by landing. Each time the client starts or stops flying on its own, it tells the server
 * through the abilities packet ({@code onUpdateAbilities}).
 */
final class Flight {
	private static final int DOUBLE_TAP_WINDOW = 7;

	private final @NotNull PlayerBody body;
	private final @NotNull Jumping jumping;
	private int jumpTriggerTime;

	/**
	 * Creates the flight of a player.
	 *
	 * @param body the player
	 * @param jumping its jump, with which it starts flying from the ground
	 */
	Flight(@NotNull PlayerBody body, @NotNull Jumping jumping) {
		this.body = body;
		this.jumping = jumping;
	}

	/**
	 * Toggles flight with the jump key of this tick, for a player that may fly.
	 *
	 * @param wasJumping whether the jump key was held in the previous tick
	 */
	void toggle(boolean wasJumping) {
		PlayerState server = body.server();
		if (!server.mayFly()) return;
		if (server.isSpectator()) {
			if (server.flying()) return;

			server.flying(true);
			body.updateAbilities();
			return;
		}
		if (wasJumping || !body.keys().jump()) return;
		if (jumpTriggerTime == 0) {
			jumpTriggerTime = DOUBLE_TAP_WINDOW;
			return;
		}

		server.flying(!server.flying());
		if (server.flying() && body.onGround()) jumping.jumpFromGround();
		body.updateAbilities();
		jumpTriggerTime = 0;
	}

	/**
	 * Steers a flying player up with jump and down with sneak, by three times its flying speed.
	 */
	void steer() {
		PlayerState server = body.server();
		if (!server.flying()) return;

		int direction = (body.keys().sneak() ? -1 : 0) + (body.keys().jump() ? 1 : 0);
		if (direction != 0) body.addDeltaMovement(0.0, direction * server.flyingSpeed() * 3.0F, 0.0);
	}

	/**
	 * Counts the double tap's window down.
	 */
	void countDown() {
		if (jumpTriggerTime > 0) jumpTriggerTime--;
	}

	/**
	 * Lands the flight of a player that is not a spectator and tells the server, after the player's own movement.
	 */
	void land() {
		if (!body.onGround() || !body.server().flying() || body.server().isSpectator()) return;

		body.server().flying(false);
		body.updateAbilities();
	}
}
