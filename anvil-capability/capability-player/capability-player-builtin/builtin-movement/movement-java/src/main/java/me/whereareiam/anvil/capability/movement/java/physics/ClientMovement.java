package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.channel.MovementState;
import me.whereareiam.anvil.capability.movement.channel.PhysicsChange;
import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerBody;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerStatePackets;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.edition.java.physics.world.BlockReader;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.LocalPlayer;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerRot;
import me.whereareiam.anvil.player.api.channel.Subscription;
import me.whereareiam.anvil.player.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.player.api.model.player.ViewRotation;
import me.whereareiam.anvil.player.api.worker.OperationRegistry;
import me.whereareiam.anvil.player.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.player.api.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The movement of one player's client on its {@link LocalPlayer}: the operations a test calls and, with physics on, the
 * player's own tick within the client's tick, which moves the client's player as the vanilla client of its Minecraft
 * version moves it. The client's player accepts the server's teleports and ticks for every Java player; the movement
 * follows what the server sets of the player that only physics reads ({@link PlayerState}), the teleports and velocities
 * the client took and its connections, and runs everything under the client's lock.
 */
public final class ClientMovement implements WorkerBinding {
	private final @NotNull PlayerBindingContext<Object> player;
	private final @NotNull JavaProtocol protocol;
	private final @NotNull LocalPlayer client;
	private final @NotNull ClientSimulation simulation;
	private final @NotNull PhysicsSwitch physics;
	private final @NotNull Subscription subscriptions;
	private long ticks;
	private @NotNull MovementInput input = MovementInput.NONE;

	private ClientMovement(@NotNull PlayerBindingContext<Object> player) {
		this.player = player;
		this.protocol = player.protocol(JavaProtocol.class);
		this.client = protocol.player();
		PlayerState state = new PlayerState(player.clientVersion(), client);
		this.simulation = new ClientSimulation(new MovementRules(player.clientVersion()), state, client,
				protocol.supports(ServerboundPlayerInput.class));
		this.physics = new PhysicsSwitch(protocol, simulation::stop);
		this.subscriptions = Subscription.all(
				PlayerStatePackets.follow(state, protocol),
				protocol.listen(ClientboundLogin.class, login -> simulation.forget()),
				protocol.listen(ClientboundRespawn.class, respawn -> simulation.forget()),
				protocol.listen(ClientboundPlayerPosition.class, teleport -> simulation.teleported(physics.blocks())),
				protocol.listen(ClientboundSetEntityMotion.class, motion -> simulation.motion()),
				player.bindConnection(() -> () -> client.run(simulation::forget)),
				client.onTick(this::tick)
		);
	}

	/**
	 * Binds the movement of a player that speaks the Java Edition protocol and registers its operations.
	 *
	 * @param player the player's binding context
	 * @param operations the player's operation registry
	 * @return the movement, which stops ticking the player and closes its listeners when closed
	 * @throws ProtocolUnavailableException when the player does not speak the Java Edition protocol
	 */
	public static @NotNull ClientMovement bind(
			@NotNull PlayerBindingContext<Object> player,
			@NotNull OperationRegistry operations
	) {
		ClientMovement movement = new ClientMovement(player);
		LocalPlayer client = movement.client;
		operations.register(MovementChannel.MOVE, position -> answering(client, () -> movement.move(position)));
		operations.register(MovementChannel.LOOK, rotation -> answering(client, () -> movement.look(rotation)));
		operations.register(MovementChannel.PHYSICS, on -> new PhysicsChange(client.get(() -> movement.physics(on))));
		operations.register(MovementChannel.INPUT, keys -> answering(client, () -> movement.input = keys));
		operations.register(MovementChannel.STATE, ignored -> client.get(movement::state));
		return movement;
	}

	/**
	 * Runs an operation that answers nothing under the client's lock, as the channel expects of an operation whose
	 * answer is {@code Void}.
	 */
	private static @Nullable Void answering(@NotNull LocalPlayer client, @NotNull Runnable command) {
		client.run(command);
		return null;
	}

	private void move(@NotNull Position to) {
		protocol.send(ServerboundMovePlayerPosRot.builder()
				.position(new Vec3d(to.getX(), to.getY(), to.getZ()))
				.yaw(to.getYaw())
				.pitch(to.getPitch())
				.onGround(to.isOnGround())
				.build());
		simulation.moveTo(to, physics.blocks());
	}

	private void look(@NotNull ViewRotation rotation) {
		if (simulation.look(rotation, physics.blocks())) return;

		protocol.send(ServerboundMovePlayerRot.builder().yaw(rotation.getYaw()).pitch(rotation.getPitch())
				.onGround(client.onGround()).build());
	}

	/**
	 * Turns physics on or off, and tells why it cannot run for the player's release, or {@code null}.
	 */
	private @Nullable String physics(boolean on) {
		if (on) return physics.turnOn();

		physics.turnOff(null);
		return null;
	}

	private @NotNull MovementState state() {
		Vec3d velocity = client.velocity();
		return MovementState.builder().physics(physics.isEnabled()).failure(physics.getFailure()).ticks(ticks)
				.position(simulation.current()).motion(new Motion(velocity.getX(), velocity.getY(), velocity.getZ()))
				.build();
	}

	/**
	 * Runs the player's own tick within the client's tick, with physics on: a failing tick turns physics off, and the
	 * client goes on ticking.
	 */
	private void tick() {
		BlockReader blocks = physics.blocks();
		if (blocks == null) return;

		try {
			if (simulation.tick(blocks, input, protocol::send)) ticks++;
		} catch (RuntimeException failure) {
			physics.turnOff(failure.toString());
			System.err.println("Client physics of player '" + player.name() + "' turned off after a tick failed: "
					+ failure);
		}
	}

	/**
	 * Returns the simulated player's body, as the reference traces read it after each tick, or {@code null} without
	 * physics.
	 */
	@Nullable PlayerBody body() {
		return client.get(simulation::body);
	}

	/**
	 * Turns physics off and closes the listeners and the player's tick. The listeners close outside the client's lock:
	 * the player's connection ends its bindings under a lock of its own and then calls the movement, so holding the lock
	 * while closing them would deadlock with a connection that ends at the same time.
	 */
	@Override
	public void close() {
		client.run(() -> physics.turnOff(null));
		subscriptions.close();
	}
}
