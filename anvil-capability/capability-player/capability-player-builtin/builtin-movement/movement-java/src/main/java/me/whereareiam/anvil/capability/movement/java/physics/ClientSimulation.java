package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerBody;
import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerSimulation;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.java.api.LocalPlayer;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.geometry.Vector;
import me.whereareiam.anvil.protocol.java.physics.world.BlockReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * The client's own player while client physics moves it: the simulated player, which starts where the client's
 * {@link LocalPlayer} is and leaves it where each tick moved it, and the {@link PositionReporter} of what the client
 * told the server. The client's player stays the one record of where the client is: whatever the server or a test
 * changes of it reaches the simulated player at once, and the simulated player's tick writes its position, view,
 * velocity and ground state back. Each login, respawn and closed connection makes a new player, which forgets the
 * simulated player and what it told.
 *
 * <p>Not thread-safe; its owner runs it under the client's lock.</p>
 */
final class ClientSimulation {
	private final @NotNull MovementRules rules;
	private final @NotNull PlayerState state;
	private final @NotNull LocalPlayer client;
	private final boolean inputPackets;
	private @NotNull PositionReporter reporter;
	private @Nullable PlayerSimulation simulation;

	/**
	 * @param rules the client's rules
	 * @param state what the server set of the player that physics reads
	 * @param client the client's own player
	 * @param inputPackets whether the release carries the held keys
	 */
	ClientSimulation(
			@NotNull MovementRules rules,
			@NotNull PlayerState state,
			@NotNull LocalPlayer client,
			boolean inputPackets
	) {
		this.rules = rules;
		this.state = state;
		this.client = client;
		this.inputPackets = inputPackets;
		this.reporter = new PositionReporter(rules, client, inputPackets);
	}

	/**
	 * Returns where the client is, or {@code null} before it was placed on its connection.
	 */
	@Nullable Position current() {
		Vec3d at = client.position().orElse(null);
		if (at == null) return null;

		ViewRotation view = client.rotation();
		return Position.builder().x(at.getX()).y(at.getY()).z(at.getZ()).yaw(view.getYaw()).pitch(view.getPitch())
				.onGround(client.onGround()).build();
	}

	/**
	 * Returns the simulated player's body, or {@code null} without physics.
	 */
	@Nullable PlayerBody body() {
		return simulation == null ? null : simulation.body();
	}

	/**
	 * Places the client at rest where a test moved it with a movement packet; with physics on, the simulated player goes
	 * on from there.
	 *
	 * @param physics the blocks the client knows, or {@code null} while physics is off
	 */
	void moveTo(@NotNull Position position, @Nullable BlockReader physics) {
		client.position(new Vec3d(position.getX(), position.getY(), position.getZ()));
		client.rotation(new ViewRotation(position.getYaw(), position.getPitch()));
		client.onGround(position.isOnGround());
		client.velocity(Vec3d.ZERO);
		reporter.sent(position);
		PlayerSimulation simulated = simulate(physics);
		if (simulated != null) simulated.moveTo(position);
	}

	/**
	 * Turns the client's view.
	 *
	 * @param physics the blocks the client knows, or {@code null} while physics is off
	 * @return whether the simulated player turned, which reports it with its next tick; otherwise the owner sends the
	 * rotation, which the client remembers as sent
	 */
	boolean look(@NotNull ViewRotation rotation, @Nullable BlockReader physics) {
		client.rotation(rotation);
		PlayerSimulation simulated = simulate(physics);
		if (simulated != null) {
			simulated.look(rotation.getYaw(), rotation.getPitch());
			return true;
		}
		reporter.turned(rotation.getYaw(), rotation.getPitch(), client.onGround());
		return false;
	}

	/**
	 * Follows a server teleport the client accepted: with physics on, the simulated player goes on from where it put the
	 * client, with the velocity it left.
	 *
	 * @param physics the blocks the client knows, or {@code null} while physics is off
	 */
	void teleported(@Nullable BlockReader physics) {
		Position at = current();
		if (at == null) return;

		Vector velocity = vector(client.velocity());
		if (simulation == null && physics != null)
			simulation = PlayerSimulation.start(rules, state, physics, at, velocity);
		if (simulation != null)
			simulation.place(new Vector(at.getX(), at.getY(), at.getZ()), velocity, at.getYaw(), at.getPitch());
	}

	/**
	 * Follows the velocity the server gave the client's own entity, which the simulated player takes.
	 */
	void motion() {
		if (simulation != null) simulation.body().setDeltaMovement(vector(client.velocity()));
	}

	/**
	 * Runs the simulated player's tick within the client's tick of its player, starting it where the client is, writes
	 * where it left the player back to the client, and sends the packets the tick reports: the flight changes the client
	 * told during the tick, which its {@code aiStep} sends at once, and, unless the player is dead, those of the tick's
	 * end.
	 *
	 * @return whether the player ticked
	 */
	boolean tick(@NotNull BlockReader reader, @NotNull MovementInput input, @NotNull Consumer<ServerboundPacket> send) {
		PlayerSimulation ticking = simulate(reader);
		if (ticking == null) return false;

		ticking.tick(input);
		PlayerBody body = ticking.body();
		client.position(vec3d(body.getPosition()));
		client.rotation(new ViewRotation(body.getYaw(), body.getPitch()));
		client.onGround(body.isOnGround());
		client.velocity(vec3d(body.getDeltaMovement()));
		body.abilityUpdates().forEach(send);
		if (!client.isDead()) reporter.report(body).forEach(send);
		return true;
	}

	/**
	 * Stops simulating; the client stays where the simulated player left it.
	 */
	void stop() {
		simulation = null;
	}

	/**
	 * Makes a new player, as a login, a respawn or a new connection does: no simulated player, nothing told.
	 */
	void forget() {
		simulation = null;
		reporter = new PositionReporter(rules, client, inputPackets);
	}

	/**
	 * Returns the simulated player, starting it with physics on where the client is.
	 */
	private @Nullable PlayerSimulation simulate(@Nullable BlockReader physics) {
		Position at = current();
		if (simulation == null && physics != null && at != null)
			simulation = PlayerSimulation.start(rules, state, physics, at, vector(client.velocity()));
		return simulation;
	}

	private static @NotNull Vector vector(@NotNull Vec3d vector) {
		return new Vector(vector.getX(), vector.getY(), vector.getZ());
	}

	private static @NotNull Vec3d vec3d(@NotNull Vector vector) {
		return new Vec3d(vector.x, vector.y, vector.z);
	}
}
