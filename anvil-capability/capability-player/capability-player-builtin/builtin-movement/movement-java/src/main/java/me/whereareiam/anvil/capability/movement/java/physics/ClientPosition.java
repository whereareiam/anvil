package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerSimulation;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.world.BlockReader;
import me.whereareiam.anvil.edition.java.protocol.JavaWorld;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.player.api.model.player.ViewRotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Where the player's client is on its current connection: the position the server or a test last placed it at, or,
 * while physics runs, the simulated player that moves from there, with the {@link PositionReporter} of what the client
 * told the server, as a vanilla client's player keeps it. The client knows no position until it is placed, and each
 * login, respawn and closed connection makes a new player, which forgets the position and what it told.
 *
 * <p>Not thread-safe; its owner serializes packets, operations and ticks.</p>
 */
final class ClientPosition {
	private final @NotNull MovementRules rules;
	private final @NotNull PlayerState state;
	private final boolean inputPackets;
	private @NotNull PositionReporter reporter;
	private @Nullable Position known;
	private @Nullable PlayerSimulation simulation;

	ClientPosition(@NotNull MovementRules rules, @NotNull PlayerState state, boolean inputPackets) {
		this.rules = rules;
		this.state = state;
		this.inputPackets = inputPackets;
		this.reporter = new PositionReporter(rules, state, inputPackets);
	}

	/**
	 * Returns where the client is, or {@code null} before it was placed on its connection.
	 */
	@Nullable Position current() {
		return simulation == null ? known : simulation.position();
	}

	/**
	 * Returns the simulated player's velocity, zero without physics.
	 */
	@NotNull Vector velocity() {
		return simulation == null ? Vector.ZERO : simulation.body().deltaMovement();
	}

	/**
	 * Places the client where a test moved it with a movement packet; with physics on, the simulated player goes on from
	 * there at rest.
	 *
	 * @param physics the blocks the client knows, or {@code null} while physics is off
	 */
	void moveTo(@NotNull Position position, @Nullable BlockReader physics) {
		known = position;
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
		PlayerSimulation simulated = simulate(physics);
		if (simulated != null) {
			simulated.look(rotation.yaw(), rotation.pitch());
			return true;
		}
		if (known != null) known = Position.builder().x(known.x()).y(known.y()).z(known.z())
				.yaw(rotation.yaw()).pitch(rotation.pitch()).onGround(known.onGround()).build();
		reporter.turned(rotation.yaw(), rotation.pitch(), known != null && known.onGround());
		return false;
	}

	/**
	 * Places the client where a server teleport put it, with the velocity the teleport left it; with physics on, the
	 * simulated player goes on from there.
	 *
	 * @param physics the blocks the client knows, or {@code null} while physics is off
	 */
	void teleported(@NotNull Position position, @NotNull Vector velocity, @Nullable BlockReader physics) {
		known = position;
		if (simulation == null && physics != null)
			simulation = PlayerSimulation.start(rules, state, physics, position, velocity);
		Vector at = new Vector(position.x(), position.y(), position.z());
		if (simulation != null) simulation.place(at, velocity, position.yaw(), position.pitch());
	}

	/**
	 * Sets the velocity the server gives the simulated player, which has none without physics.
	 */
	void velocity(@NotNull Vector velocity) {
		if (simulation != null) simulation.body().deltaMovement(velocity);
	}

	/**
	 * Runs the simulated player's tick where it is in a loaded chunk, starting it where the client was placed, and sends
	 * the packets the tick reports: the flight changes the client told during the tick, which its {@code aiStep} sends
	 * at once, and, unless the player is dead, those of the tick's end.
	 *
	 * @return whether the player ticked
	 */
	boolean tick(
			@NotNull BlockReader reader,
			@NotNull JavaWorld world,
			@NotNull MovementInput input,
			@NotNull Consumer<ServerboundPacket> send
	) {
		PlayerSimulation ticking = simulate(reader);
		if (ticking == null || !world.isLoaded(ticking.body().chunk())) return false;

		ticking.tick(input);
		ticking.body().abilityUpdates().forEach(send);
		if (!state.dead()) reporter.report(ticking.body()).forEach(send);
		return true;
	}

	/**
	 * Returns the simulated player, starting it with physics on where the client was placed.
	 */
	private @Nullable PlayerSimulation simulate(@Nullable BlockReader physics) {
		if (simulation == null && physics != null && known != null)
			simulation = PlayerSimulation.start(rules, state, physics, known, Vector.ZERO);
		return simulation;
	}

	/**
	 * Stops simulating; the client stays where the simulated player was.
	 */
	void stop() {
		known = current();
		simulation = null;
	}

	/**
	 * Makes a new player, as a login, a respawn or a new connection does: no position, nothing told.
	 */
	void forget() {
		known = null;
		simulation = null;
		reporter = new PositionReporter(rules, state, inputPackets);
	}
}
