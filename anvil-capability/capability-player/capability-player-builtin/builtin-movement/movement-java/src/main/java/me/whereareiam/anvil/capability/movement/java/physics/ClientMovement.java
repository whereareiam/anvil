package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.model.Motion;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.MovementState;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.JavaWorld;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.entity.ClientboundSetEntityMotion;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundMovePlayerRot;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import me.whereareiam.anvil.protocol.java.data.BlockRegistry;
import me.whereareiam.anvil.protocol.java.data.JavaBlockData;
import me.whereareiam.anvil.protocol.java.data.exception.BlockDataUnavailableException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The movement of one player's client: where it is, the keys it holds and, with client physics on, the vanilla
 * client's tick that moves it 20 times a second. It answers every server teleport as the vanilla client does: before
 * 26.3 the acceptance the player's library sends is followed by the position the teleport left it at, from 26.3 the
 * acceptance carries that position itself. It follows the
 * player's connections: each login and respawn forgets the position until the server places the player again.
 *
 * <p>Packets, operations and ticks arrive on different threads; the movement serializes them on its own monitor,
 * as the vanilla client runs its packets between ticks.</p>
 */
public final class ClientMovement implements WorkerBinding {
	private final @NotNull PlayerBindingContext<Object> player;
	private final @NotNull JavaProtocol protocol;
	private final @NotNull Clock clock;
	private final @Nullable MovementRules rules;
	private final @Nullable Physics physics;
	private final @NotNull String unavailable;
	private final @NotNull Subscription subscriptions;

	private boolean enabled;
	private long ticks;
	private @NotNull MovementInput input = MovementInput.NONE;
	/**
	 * The protocol ID of the player's own entity, which the server may choose negative, or {@code null} until the
	 * player joined a level.
	 */
	private @Nullable Integer entityId;
	private @Nullable Position known;
	private @Nullable PlayerSimulation simulation;
	private @Nullable PositionReporter reporter;
	private @Nullable Subscription ticking;

	private ClientMovement(@NotNull PlayerBindingContext<Object> player, @NotNull Clock clock) {
		this.player = player;
		this.protocol = player.protocol(JavaProtocol.class);
		this.clock = clock;
		MovementRules release = null;
		Physics resolved = null;
		String reason = "";
		try {
			release = new MovementRules(player.minecraftVersion());
			resolved = Physics.resolve(player, release);
		} catch (ProtocolUnavailableException | BlockDataUnavailableException exception) {
			reason = exception.getMessage();
		}
		this.rules = release;
		this.physics = resolved;
		this.unavailable = reason;
		this.subscriptions = Subscription.all(
				protocol.listen(ClientboundLogin.class, this::login),
				protocol.listen(ClientboundRespawn.class, ignored -> respawn()),
				protocol.listen(ClientboundPlayerPosition.class, this::teleport),
				protocol.listen(ClientboundSetEntityMotion.class, this::motion),
				player.bindConnection(() -> this::disconnected)
		);
	}

	/**
	 * Binds a player's movement and registers its operations.
	 *
	 * @param player the player's binding context, which speaks the Java Edition protocol
	 * @param operations the player's operation registry
	 * @param clock the clock that runs the ticks of client physics
	 * @return the movement, which closes its listeners and stops its ticks when closed
	 * @throws ProtocolUnavailableException when the player does not speak the Java Edition protocol
	 */
	public static @NotNull ClientMovement bind(
			@NotNull PlayerBindingContext<Object> player,
			@NotNull OperationRegistry operations,
			@NotNull Clock clock
	) {
		ClientMovement movement = new ClientMovement(player, clock);
		operations.register(MovementChannel.MOVE, position -> {
			movement.move(position);
			return null;
		});
		operations.register(MovementChannel.PHYSICS, enabled -> {
			movement.physics(enabled);
			return null;
		});
		operations.register(MovementChannel.INPUT, keys -> {
			movement.input(keys);
			return null;
		});
		operations.register(MovementChannel.LOOK, rotation -> {
			movement.look(rotation.getYaw(), rotation.getPitch());
			return null;
		});
		operations.register(MovementChannel.STATE, ignored -> movement.state());

		return movement;
	}

	synchronized void move(@NotNull Position position) {
		protocol.send(ServerboundMovePlayerPosRot.builder()
				.position(new Vec3d(position.getX(), position.getY(), position.getZ()))
				.yaw(position.getYaw())
				.pitch(position.getPitch())
				.onGround(position.isOnGround())
				.horizontalCollision(false)
				.build());
		player.viewRotation(new ViewRotation(position.getYaw(), position.getPitch()));
		known = position;
		if (!enabled) return;

		PlayerSimulation placed = simulation();
		if (placed == null) return;

		placed.moveTo(position.getX(), position.getY(), position.getZ(), position.getYaw(), position.getPitch(), position.isOnGround());
		reporter().sent(position.getX(), position.getY(), position.getZ(), position.getYaw(), position.getPitch(), position.isOnGround());
	}

	synchronized void physics(boolean enabled) {
		if (enabled && physics == null)
			throw new IllegalStateException("Client physics is unavailable: " + unavailable);
		if (enabled == this.enabled) return;

		this.enabled = enabled;
		if (enabled) {
			ticking = clock.start(this::scheduledTick);
			return;
		}

		if (simulation != null) known = position(simulation);
		simulation = null;
		reporter = null;
		stopTicking();
	}

	synchronized void input(@NotNull MovementInput input) {
		this.input = input;
	}

	synchronized void look(float yaw, float pitch) {
		player.viewRotation(new ViewRotation(yaw, pitch));
		PlayerSimulation current = enabled ? simulation() : null;
		if (current != null) {
			current.look(yaw, pitch);
			return;
		}

		boolean onGround = known != null && known.isOnGround();
		protocol.send(ServerboundMovePlayerRot.builder().yaw(yaw).pitch(pitch).onGround(onGround).horizontalCollision(false).build());
		if (known != null) known = known.toBuilder().yaw(yaw).pitch(pitch).build();
	}

	synchronized @NotNull MovementState state() {
		PlayerSimulation current = simulation;
		Motion motion = current == null
				? new Motion(0.0, 0.0, 0.0)
				: new Motion(current.deltaMovement().x, current.deltaMovement().y, current.deltaMovement().z);

		return MovementState.builder()
				.physics(enabled)
				.physicsUnavailable(physics == null ? unavailable : null)
				.ticks(ticks)
				.position(current == null ? known : position(current))
				.motion(motion)
				.build();
	}

	private void scheduledTick() {
		try {
			tick();
		} catch (RuntimeException failure) {
			System.err.println("Client physics of player '" + player.name() + "' failed a tick: " + failure);
		}
	}

	/**
	 * Runs one tick of the client: the player's own tick where the client is in a loaded chunk, and, from 1.21.2, the
	 * end of the client tick while it is in a world.
	 */
	synchronized void tick() {
		if (!enabled || physics == null || entityId == null || physics.world().dimension().isEmpty()) return;

		PlayerSimulation current = simulation();
		if (current != null && physics.view().hasChunkAt(Fluids.floor(current.x()), Fluids.floor(current.z()))) {
			float yaw = current.yaw();
			float pitch = current.pitch();
			current.tick(input);
			ticks++;
			for (ServerboundPacket packet : reporter().report(current)) protocol.send(packet);
			if (yaw != current.yaw() || pitch != current.pitch()) player.viewRotation(new ViewRotation(current.yaw(), current.pitch()));
		}

		if (physics.rules().playerInputPackets()) protocol.send(new ServerboundClientTickEnd());
	}

	private synchronized void login(@NotNull ClientboundLogin login) {
		entityId = login.getEntityId();
		forget();
	}

	private synchronized void respawn() {
		forget();
	}

	private synchronized void disconnected() {
		entityId = null;
		forget();
	}

	private void forget() {
		known = null;
		simulation = null;
		reporter = null;
	}

	/**
	 * Sets the velocity the server gives the player's own entity, as {@code ClientPacketListener.handleSetEntityMotion}
	 * does through {@code Entity.lerpMotion}; the server sends it, for example, when it hurt the player.
	 */
	private synchronized void motion(@NotNull ClientboundSetEntityMotion motion) {
		Integer own = entityId;
		if (simulation == null || own == null || own != motion.getEntityId()) return;

		Vec3d movement = motion.getMovement();
		simulation.deltaMovement(new Vector(movement.getX(), movement.getY(), movement.getZ()));
	}

	/**
	 * Applies a server teleport as {@code ClientPacketListener.handleMovePlayer} does and confirms it with the
	 * position it left the player at, on the ground state {@code false}.
	 */
	private synchronized void teleport(@NotNull ClientboundPlayerPosition teleport) {
		PlayerSimulation current = enabled ? simulation : null;
		double x = current != null ? current.x() : known != null ? known.getX() : 0.0;
		double y = current != null ? current.y() : known != null ? known.getY() : 0.0;
		double z = current != null ? current.z() : known != null ? known.getZ() : 0.0;
		float yaw = current != null ? current.yaw() : known != null ? known.getYaw() : 0.0F;
		float pitch = current != null ? current.pitch() : known != null ? known.getPitch() : 0.0F;
		Vector delta = current != null ? current.deltaMovement() : Vector.ZERO;
		Set<Relative> relatives = teleport.getRelatives();

		Vec3d target = teleport.getPosition();
		double newX = (relatives.contains(Relative.X) ? x : 0.0) + target.getX();
		double newY = (relatives.contains(Relative.Y) ? y : 0.0) + target.getY();
		double newZ = (relatives.contains(Relative.Z) ? z : 0.0) + target.getZ();
		float newYaw = (relatives.contains(Relative.YAW) ? yaw : 0.0F) + teleport.getYaw();
		float newPitch = (relatives.contains(Relative.PITCH) ? pitch : 0.0F) + teleport.getPitch();
		if (clampsPitch()) newPitch = Math.clamp(newPitch, -90.0F, 90.0F);
		if (relatives.contains(Relative.ROTATE_DELTA)) {
			Trigonometry trigonometry = new Trigonometry(true);
			delta = delta.xRot((float) Math.toRadians(pitch - newPitch), trigonometry);
			delta = delta.yRot((float) Math.toRadians(yaw - newYaw), trigonometry);
		}
		Vec3d velocity = teleport.getDeltaMovement();
		Vector newDelta = new Vector(
				(relatives.contains(Relative.DELTA_X) ? delta.x : 0.0) + velocity.getX(),
				(relatives.contains(Relative.DELTA_Y) ? delta.y : 0.0) + velocity.getY(),
				(relatives.contains(Relative.DELTA_Z) ? delta.z : 0.0) + velocity.getZ()
		);

		boolean onGround = current != null ? current.onGround() : known != null && known.isOnGround();
		known = Position.builder().x(newX).y(newY).z(newZ).yaw(newYaw).pitch(newPitch).onGround(onGround).build();
		if (current != null) current.place(newX, newY, newZ, newDelta, newYaw, newPitch);
		else if (enabled) placeNew(newX, newY, newZ, newDelta, newYaw, newPitch);
		if (rules != null && !rules.teleportConfirmation()) return;

		protocol.send(ServerboundMovePlayerPosRot.builder()
				.position(new Vec3d(newX, newY, newZ))
				.yaw(newYaw)
				.pitch(newPitch)
				.onGround(false)
				.horizontalCollision(false)
				.build());
	}

	/**
	 * From 1.21.2 a teleport clamps the pitch to a quarter turn ({@code PositionMoveRotation.calculateAbsolute}); a
	 * player whose context names no Minecraft version clamps it where its protocol carries the 1.21.2 tick end.
	 */
	private boolean clampsPitch() {
		if (rules != null) return rules.pathBlockEffects();

		return protocol.supports(ServerboundClientTickEnd.class);
	}

	private @Nullable PlayerSimulation simulation() {
		if (simulation != null || physics == null || known == null || entityId == null) return simulation;

		Position at = known;
		placeNew(at.getX(), at.getY(), at.getZ(), Vector.ZERO, at.getYaw(), at.getPitch());
		reporter().sent(at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch(), at.isOnGround());
		return simulation;
	}

	private void placeNew(double x, double y, double z, @NotNull Vector delta, float yaw, float pitch) {
		if (physics == null) return;

		PlayerSimulation placed = new PlayerSimulation(physics.rules(), physics.view(), x, y, z, yaw, pitch);
		placed.place(x, y, z, delta, yaw, pitch);
		simulation = placed;
	}

	private @NotNull PositionReporter reporter() {
		if (reporter == null && physics != null && entityId != null)
			reporter = new PositionReporter(physics.rules(), entityId);
		if (reporter == null) throw new IllegalStateException("Client physics is unavailable: " + unavailable);

		return reporter;
	}

	private static @NotNull Position position(@NotNull PlayerSimulation simulation) {
		return Position.builder()
				.x(simulation.x())
				.y(simulation.y())
				.z(simulation.z())
				.yaw(simulation.yaw())
				.pitch(simulation.pitch())
				.onGround(simulation.onGround())
				.build();
	}

	private void stopTicking() {
		Subscription current = ticking;
		ticking = null;
		if (current != null) current.close();
	}

	@Override
	public synchronized void close() {
		stopTicking();
		subscriptions.close();
	}

	/**
	 * Runs the ticks of client physics 20 times a second, as the vanilla client's game loop does.
	 */
	@FunctionalInterface
	public interface Clock {
		/**
		 * Starts running a tick every 50 milliseconds.
		 *
		 * @param tick the tick to run
		 * @return registration that stops the ticks when closed
		 */
		@NotNull Subscription start(@NotNull Runnable tick);
	}

	/**
	 * What client physics needs of the player's release: the world its client knows, read by the release's block
	 * data, and the rules of how its client moves.
	 */
	private record Physics(@NotNull JavaWorld world, @NotNull BlockView view, @NotNull MovementRules rules) {
		private static @NotNull Physics resolve(@NotNull PlayerBindingContext<Object> player, @NotNull MovementRules rules) {
			JavaWorld world = player.protocol(JavaWorld.class);
			BlockRegistry registry = JavaBlockData.shipped().require(rules.version());

			return new Physics(world, new BlockView(world, registry), rules);
		}
	}
}
