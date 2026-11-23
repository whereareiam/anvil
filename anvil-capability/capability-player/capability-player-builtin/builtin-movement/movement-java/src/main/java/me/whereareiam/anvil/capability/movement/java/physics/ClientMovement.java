package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.channel.MovementState;
import me.whereareiam.anvil.capability.movement.channel.PhysicsChange;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerStatePackets;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.edition.java.data.block.JavaBlockData;
import me.whereareiam.anvil.edition.java.data.block.exception.BlockDataUnavailableException;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.world.BlockReader;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.JavaWorld;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerAbilities;
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

import java.time.Duration;
import java.util.List;

/**
 * The movement of one player's client: the operations a test calls and, with physics on, the vanilla client's tick its
 * {@link PhysicsLoop} runs 20 times a second. It follows the server's state of the player ({@link PlayerState}), its
 * entity's velocity and its connections, where the client is ({@link ClientPosition}), and alone resolves server
 * teleports, answering each before 26.3 with the position it left the player at, as {@code handleMovePlayer} does.
 */
public final class ClientMovement implements WorkerBinding {
	private final @NotNull PlayerBindingContext<Object> player;
	private final @NotNull JavaProtocol protocol;
	private final @NotNull JavaWorld world;
	private final @NotNull MovementRules rules;
	private final @NotNull PlayerState state;
	private final @NotNull ClientPosition position;
	private final @NotNull PhysicsLoop loop;
	private final @NotNull Subscription subscriptions;
	private boolean enabled;
	private @Nullable String failure;
	private long ticks;
	private @NotNull MovementInput input = MovementInput.NONE;
	private @Nullable BlockReader reader;

	private ClientMovement(@NotNull PlayerBindingContext<Object> player, @NotNull Duration period) {
		this.player = player;
		this.protocol = player.protocol(JavaProtocol.class);
		this.world = protocol.world();
		this.rules = new MovementRules(player.clientVersion());
		this.state = new PlayerState(player.clientVersion());
		this.position = new ClientPosition(rules, state, protocol.supports(ServerboundPlayerInput.class));
		this.loop = new PhysicsLoop(player.name(), period, this::tick, this::failed);
		this.subscriptions = Subscription.all(
				PlayerStatePackets.follow(state, protocol, loop::run),
				protocol.listen(ClientboundLogin.class, login -> loop.run(position::forget)),
				protocol.listen(ClientboundRespawn.class, respawn -> loop.run(position::forget)),
				protocol.listen(ClientboundPlayerPosition.class, teleport -> loop.run(() -> teleport(teleport))),
				protocol.listen(ClientboundSetEntityMotion.class, motion -> loop.run(() -> motion(motion))),
				player.bindConnection(() -> () -> loop.run(() -> {
					state.disconnected();
					position.forget();
				}))
		);
	}

	/**
	 * Binds the movement of a player that speaks the Java Edition protocol and registers its operations.
	 *
	 * @param player the player's binding context
	 * @param operations the player's operation registry
	 * @return the movement, which stops its ticks and closes its listeners when closed
	 * @throws ProtocolUnavailableException when the player does not speak the Java Edition protocol
	 */
	public static @NotNull ClientMovement bind(
			@NotNull PlayerBindingContext<Object> player,
			@NotNull OperationRegistry operations
	) {
		return bind(player, operations, PhysicsLoop.PERIOD);
	}

	static @NotNull ClientMovement bind(
			@NotNull PlayerBindingContext<Object> player,
			@NotNull OperationRegistry operations,
			@NotNull Duration period
	) {
		ClientMovement movement = new ClientMovement(player, period);
		PhysicsLoop loop = movement.loop;
		operations.register(MovementChannel.MOVE, position -> loop.answering(() -> movement.move(position)));
		operations.register(MovementChannel.LOOK, rotation -> loop.answering(() -> movement.look(rotation)));
		operations.register(MovementChannel.PHYSICS, on -> new PhysicsChange(loop.get(() -> movement.physics(on))));
		operations.register(MovementChannel.INPUT, keys -> loop.answering(() -> movement.input = keys));
		operations.register(MovementChannel.STATE, ignored -> loop.get(movement::state));
		return movement;
	}

	private void move(@NotNull Position to) {
		protocol.send(ServerboundMovePlayerPosRot.builder()
				.position(new Vec3d(to.x(), to.y(), to.z()))
				.yaw(to.yaw())
				.pitch(to.pitch())
				.onGround(to.onGround())
				.build());
		player.viewRotation(new ViewRotation(to.yaw(), to.pitch()));
		position.moveTo(to, enabled ? reader : null);
	}

	private void look(@NotNull ViewRotation rotation) {
		player.viewRotation(rotation);
		if (position.look(rotation, enabled ? reader : null)) return;

		boolean onGround = position.current() != null && position.current().onGround();
		protocol.send(ServerboundMovePlayerRot.builder().yaw(rotation.yaw()).pitch(rotation.pitch())
				.onGround(onGround).build());
	}

	/**
	 * Turns physics on or off, and tells why it cannot run for the player's release, or {@code null}.
	 */
	private @Nullable String physics(boolean on) {
		if (!on) {
			turnOff(null);
			return null;
		}

		String unavailable = unavailable();
		if (unavailable != null || enabled) return unavailable;

		enabled = true;
		failure = null;
		loop.start();
		return null;
	}

	private @NotNull MovementState state() {
		Vector velocity = position.velocity();
		return MovementState.builder().physics(enabled).failure(failure).ticks(ticks).position(position.current())
				.motion(new Motion(velocity.x, velocity.y, velocity.z)).build();
	}

	/**
	 * Runs one tick of the client, as the loop does under the lock: the player's own tick where it is in a loaded chunk,
	 * with the packets it reports unless it is dead, then the end of the client tick where the release carries it.
	 */
	void tick() {
		if (!enabled || reader == null || state.entityId() == null || world.level().isEmpty()) return;

		if (position.tick(reader, world, input, protocol::send)) ticks++;
		if (protocol.supports(ServerboundClientTickEnd.class)) protocol.send(new ServerboundClientTickEnd());
	}

	private void failed(@NotNull Throwable cause) {
		turnOff(cause.toString());
		System.err.println("Client physics of player '" + player.name() + "' turned off after a tick failed: " + cause);
	}

	private void turnOff(@Nullable String reason) {
		position.stop();
		enabled = false;
		failure = reason;
		loop.stop();
	}

	private @Nullable String unavailable() {
		try {
			protocol.require("what client physics follows", PlayerStatePackets.PACKETS);
			protocol.require("the flight client physics toggles", List.of(ServerboundPlayerAbilities.class));
			if (reader == null) reader = new BlockReader(world, JavaBlockData.shipped().require(protocol.protocolNumber()));
			return null;
		} catch (ProtocolUnavailableException | BlockDataUnavailableException exception) {
			return exception.getMessage();
		}
	}

	/**
	 * Applies a server teleport, which {@link Teleport} resolves from where the client is, and confirms it where the
	 * client's version does.
	 */
	private void teleport(@NotNull ClientboundPlayerPosition packet) {
		Position from = position.current() == null ? Position.builder().build() : position.current();
		Teleport.Resolved resolved = Teleport.resolve(from, position.velocity(), packet, rules);
		Position at = resolved.at();
		position.teleported(at, resolved.velocity(), enabled ? reader : null);
		player.viewRotation(new ViewRotation(at.yaw(), at.pitch()));
		if (!rules.has(ClientBehaviour.TELEPORT_CONFIRMATION)) return;

		protocol.send(ServerboundMovePlayerPosRot.builder()
				.position(new Vec3d(at.x(), at.y(), at.z()))
				.yaw(at.yaw())
				.pitch(at.pitch())
				.build());
	}

	/**
	 * Takes the velocity the server gives the player's own entity ({@code handleSetEntityMotion}).
	 */
	private void motion(@NotNull ClientboundSetEntityMotion motion) {
		Vec3d movement = motion.movement();
		if (state.own(motion.entityId())) position.velocity(new Vector(movement.x(), movement.y(), movement.z()));
	}

	/**
	 * Stops the ticks for good and closes the listeners. The listeners close outside the lock: the player's connection
	 * ends its bindings under a lock of its own and then calls the movement, so holding the lock while closing them
	 * would deadlock with a connection that ends at the same time.
	 */
	@Override
	public void close() {
		loop.run(() -> enabled = false);
		loop.close();
		subscriptions.close();
	}
}
