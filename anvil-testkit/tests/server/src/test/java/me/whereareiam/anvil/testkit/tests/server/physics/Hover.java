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
 * Moves a player without client physics through the air, as a bot does: once per tick it sends a position a tenth of a
 * block further along its view at the same height, where gravity would have pulled a client down. Every judge must
 * refuse such a player.
 *
 * <p>The player moves on because Grim takes a position with its rotation within its movement threshold of the last
 * one, which a client from Minecraft 1.17 until 1.20.6 sends in addition to its tick's own, for that duplicate and
 * checks none of them ({@code CheckManagerListener.isDuplicatePacket}): a player that sent the same position over and
 * over would never be judged.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Hover {
	private static final Duration TICK = Duration.ofMillis(50);
	/**
	 * How far the player moves each tick, more than Grim's movement threshold of {@code 0.03}.
	 */
	private static final double STEP = 0.1D;
	private static final Duration DISCONNECT_REPORT = Duration.ofSeconds(2);

	/**
	 * Moves the player from the start of a lane along it, claiming to fall, until a condition holds, the player is
	 * disconnected, or the timeout passes.
	 *
	 * @param player the player, whose client physics is off
	 * @param lane the lane whose start the player hovers from
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
		Position start = Position.builder()
				.x(lane.getX())
				.y(lane.getY())
				.z(lane.getZ())
				.yaw(lane.getYaw())
				.pitch(0.0F)
				.build();

		return hold(player, start, until, timeout);
	}

	/**
	 * Moves the player from a position along its view at that height, claiming to fall, until a condition holds, the
	 * player is disconnected, or the timeout passes.
	 *
	 * @param player the player, whose client physics is off
	 * @param at where the player starts and which way it looks; its ground state is replaced by falling
	 * @param until the condition that ends the hover, such as a judge's refusal
	 * @param timeout how long the player hovers at most
	 * @return whether the condition held
	 */
	public static boolean hold(
			@NotNull SimulatedPlayer player,
			@NotNull Position at,
			@NotNull BooleanSupplier until,
			@NotNull Duration timeout
	) {
		Movement movement = player.capability(Movement.class);
		Session session = player.capability(Session.class);
		double yaw = Math.toRadians(at.getYaw());
		double stepX = -Math.sin(yaw) * STEP;
		double stepZ = Math.cos(yaw) * STEP;
		Position hovering = at.toBuilder().onGround(false).build();
		Instant deadline = Instant.now().plus(timeout);
		while (Instant.now().isBefore(deadline)) {
			if (until.getAsBoolean()) return true;
			if (!session.state().connected()) return false;

			send(movement, session, hovering);
			hovering = hovering.toBuilder().x(hovering.getX() + stepX).z(hovering.getZ() + stepZ).build();
			pause();
		}

		return until.getAsBoolean();
	}

	private static void send(Movement movement, Session session, Position position) {
		try {
			movement.move(position);
		} catch (RuntimeException failure) {
			// A judge may disconnect the player while the move is on its way; the session reports it soon after.
			try {
				session.disconnected(DISCONNECT_REPORT);
			} catch (RuntimeException connected) {
				failure.addSuppressed(connected);
				throw failure;
			}
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
