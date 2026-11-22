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
	 * Toggles flight with the jump key of this tick, for a player that may fly; a jump that auto-jump pressed toggles
	 * nothing.
	 *
	 * @param wasJumping whether the input jumped in the previous tick
	 * @param autoJumped whether auto-jump pressed jump this tick
	 */
	void toggle(boolean wasJumping, boolean autoJumped) {
		PlayerState server = body.getServer();
		if (!server.isMayFly()) return;
		if (server.isSpectator()) {
			if (server.isFlying()) return;

			server.setFlying(true);
			body.updateAbilities();
			return;
		}
		if (wasJumping || !body.isJumpDown() || autoJumped) return;
		if (jumpTriggerTime == 0) {
			jumpTriggerTime = DOUBLE_TAP_WINDOW;
			return;
		}

		server.setFlying(!server.isFlying());
		if (server.isFlying() && body.isOnGround()) jumping.jumpFromGround();
		body.updateAbilities();
		jumpTriggerTime = 0;
	}

	/**
	 * Steers a flying player up with jump and down with sneak, by three times its flying speed.
	 */
	void steer() {
		PlayerState server = body.getServer();
		if (!server.isFlying()) return;

		int direction = (body.getKeys().isSneak() ? -1 : 0) + (body.isJumpDown() ? 1 : 0);
		if (direction != 0) body.addDeltaMovement(0.0, direction * server.getFlyingSpeed() * 3.0F, 0.0);
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
		if (!body.isOnGround() || !body.getServer().isFlying() || body.getServer().isSpectator()) return;

		body.getServer().setFlying(false);
		body.updateAbilities();
	}
}
