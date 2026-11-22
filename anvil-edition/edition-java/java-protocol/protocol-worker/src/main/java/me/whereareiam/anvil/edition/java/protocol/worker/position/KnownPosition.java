package me.whereareiam.anvil.edition.java.protocol.worker.position;

import lombok.Getter;
import lombok.Setter;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import me.whereareiam.anvil.edition.java.protocol.JavaWorld;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.entity.move.ClientboundSetEntityMotion;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundAcceptTeleportation;
import me.whereareiam.anvil.edition.java.protocol.packet.player.ServerboundPlayerLoaded;
import me.whereareiam.anvil.edition.java.protocol.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.edition.java.protocol.type.world.GameEventType;
import me.whereareiam.anvil.player.api.model.player.ViewRotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Where the client's own player is on its current connection, as the client knows it: the position the server or a
 * feature last placed it at, its view, velocity and ground state, and the {@link LevelLoading} of the level it joined.
 * It alone answers server teleports: it accepts each with the position and view it left the player at and, before 26.3,
 * follows the acceptance with them in a movement packet, as {@code handleMovePlayer} does. A new player knows no
 * position, looks and moves nowhere, and waits for its level.
 *
 * <p>Not thread-safe; its owner serializes packets, operations and ticks.</p>
 */
final class KnownPosition {
	private static final ViewRotation AHEAD = new ViewRotation(0, 0);

	private final @NotNull Set<LocalPlayerBehaviour> behaviours;
	private final @NotNull JavaProtocol protocol;
	private final @NotNull ServerState server;
	@Getter
	@Setter
	private @Nullable Vec3d position;
	@Getter
	@Setter
	private @NotNull ViewRotation rotation = AHEAD;
	@Getter
	@Setter
	private boolean onGround;
	@Getter
	@Setter
	private @NotNull Vec3d velocity = Vec3d.ZERO;
	private @NotNull LevelLoading loading;

	/**
	 * Creates the position of a new player.
	 *
	 * @param behaviours the behaviours of the client's version
	 * @param protocol the player's protocol, which carries the acceptance of a teleport
	 * @param server what the server set of the player's entity, which names it
	 */
	KnownPosition(
			@NotNull Set<LocalPlayerBehaviour> behaviours,
			@NotNull JavaProtocol protocol,
			@NotNull ServerState server
	) {
		this.behaviours = behaviours;
		this.protocol = protocol;
		this.server = server;
		this.loading = new LevelLoading(behaviours);
	}

	/**
	 * Follows a received packet, once the server's state took it: a login and a respawn make a new player, a teleport is
	 * accepted, the velocity of the player's own entity is taken, and the event that starts the level's loading starts
	 * the wait for the player's section.
	 *
	 * @param packet the received packet
	 */
	void apply(@NotNull ClientboundPacket packet) {
		switch (packet) {
			case ClientboundLogin ignored -> forget();
			case ClientboundRespawn ignored -> forget();
			case ClientboundPlayerPosition teleport -> teleport(teleport);
			case ClientboundSetEntityMotion motion -> {
				if (server.own(motion.getEntityId())) velocity = motion.getMovement();
			}
			case ClientboundGameEvent event -> {
				if (event.getEvent() == GameEventType.LEVEL_CHUNKS_LOAD_START) loading.loadingStarted();
			}
			default -> {
			}
		}
	}

	/**
	 * Applies a server teleport, which {@link Teleport} resolves from where the client is, accepts it with the position
	 * and view it left the player at and, before 26.3, follows the acceptance with them in a movement packet.
	 *
	 * @param packet the server's teleport
	 */
	private void teleport(@NotNull ClientboundPlayerPosition packet) {
		Teleport.Resolved resolved = Teleport.resolve(position == null ? Vec3d.ZERO : position, rotation, velocity,
				packet, behaviours);
		position = resolved.position();
		rotation = resolved.view();
		velocity = resolved.velocity();
		protocol.send(ServerboundAcceptTeleportation.builder().id(packet.getId()).position(position)
				.yaw(rotation.getYaw()).pitch(rotation.getPitch()).build());
		if (!behaviours.contains(LocalPlayerBehaviour.TELEPORT_CONFIRMATION)) return;

		protocol.send(ServerboundMovePlayerPosRot.builder().position(position).yaw(rotation.getYaw())
				.pitch(rotation.getPitch()).build());
	}

	/**
	 * Places the player in a level the client has loaded, without the server's handshake, as a player starts on a
	 * placed course of the reference traces: at rest, having told the server nothing.
	 *
	 * @param at the position of the player's feet
	 * @param view where it looks
	 * @param ground whether it stands on the ground
	 */
	void place(@NotNull Vec3d at, @NotNull ViewRotation view, boolean ground) {
		position = at;
		rotation = view;
		onGround = ground;
		velocity = Vec3d.ZERO;
		loading.loaded();
	}

	/**
	 * Makes a new player, as a login, a respawn or a new connection does: no position, looking and moving nowhere, its
	 * level not loaded.
	 */
	void forget() {
		position = null;
		rotation = AHEAD;
		onGround = false;
		velocity = Vec3d.ZERO;
		loading = new LevelLoading(behaviours);
	}

	/**
	 * Runs the start of a client tick, the connection's tick: the client's wait for its level, which tells the server
	 * when the client loaded it.
	 *
	 * @param world the client's world
	 */
	void connectionTick(@NotNull JavaWorld world) {
		if (loading.tick(chunk(), blockY(), world, server)) protocol.send(new ServerboundPlayerLoaded());
	}

	/**
	 * Tells whether the level ticks the player: once the client knows where it is and has loaded its level, and before
	 * 1.17 only while the chunk it is in is loaded.
	 *
	 * @param world the client's world
	 * @return whether the player ticks
	 */
	boolean ticksPlayer(@NotNull JavaWorld world) {
		ChunkPosition chunk = chunk();
		if (chunk == null || !loading.isLoaded()) return false;

		return !behaviours.contains(LocalPlayerBehaviour.TICKS_ONLY_IN_LOADED_CHUNK) || world.isLoaded(chunk);
	}

	/**
	 * Renders the frame after a client tick, as far as the client's wait for its level reads it.
	 *
	 * @param world the client's world
	 */
	void frame(@NotNull JavaWorld world) {
		loading.frame(chunk(), world);
	}

	private @Nullable ChunkPosition chunk() {
		if (position == null) return null;

		return ChunkPosition.containing((int) Math.floor(position.getX()), (int) Math.floor(position.getZ()));
	}

	private int blockY() {
		return position == null ? 0 : (int) Math.floor(position.getY());
	}
}
