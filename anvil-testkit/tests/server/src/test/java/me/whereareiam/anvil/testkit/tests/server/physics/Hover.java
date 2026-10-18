package me.whereareiam.anvil.testkit.tests.server.physics;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.movement.Movement;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.capability.session.Session;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;

/**
 * Holds a player without client physics in the air, as a bot does: once per tick it sends the same position, where
 * gravity would have pulled a client down. Every judge must refuse such a player.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Hover {
	private static final Duration TICK = Duration.ofMillis(50);

	/**
	 * Sends the start of a lane as the player's position once per tick, claiming to fall, until a condition holds, the
	 * player is disconnected, or the timeout passes.
	 *
	 * @param player the player, whose client physics is off
	 * @param lane the lane whose start the player hovers at
	 * @param until the condition that ends the hover, such as a judge's refusal
	 * @param timeout how long the player hovers at most
	 * @return whether the condition held
	 */
	public static boolean hold(
			@NotNull SimulatedPlayer player,
			@NotNull PhysicsCourse.Lane lane,
			@NotNull BooleanSupplier until,
			@NotNull Duration timeout
	) {
		Movement movement = player.capability(Movement.class);
		Session session = player.capability(Session.class);
		Position hovering = Position.builder()
				.x(lane.getX())
				.y(lane.getY())
				.z(lane.getZ())
				.yaw(lane.getYaw())
				.pitch(0.0F)
				.onGround(false)
				.build();
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			if (until.getAsBoolean()) return true;
			if (!session.state().connected()) return false;

			send(movement, session, hovering);
			pause();
		}

		return until.getAsBoolean();
	}

	private static void send(Movement movement, Session session, Position position) {
		try {
			movement.move(position);
		} catch (RuntimeException failure) {
			// A judge may disconnect the player between the check and the move.
			if (session.state().connected()) throw failure;
		}
	}

	private static void pause() {
		try {
			Thread.sleep(TICK);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while the player hovers", interrupted);
		}
	}
}
