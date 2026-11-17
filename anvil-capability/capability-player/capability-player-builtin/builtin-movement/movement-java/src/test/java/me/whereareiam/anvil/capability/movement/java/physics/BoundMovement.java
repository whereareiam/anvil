package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.channel.MovementState;
import me.whereareiam.anvil.capability.movement.channel.PhysicsChange;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.player.ClientboundPlayerPosition;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundAcceptTeleportation;
import me.whereareiam.anvil.protocol.java.api.packet.player.move.ServerboundMovePlayerPosRot;
import me.whereareiam.anvil.protocol.java.api.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.protocol.java.api.type.entity.Relative;
import me.whereareiam.anvil.protocol.java.api.type.world.GameEventType;
import me.whereareiam.anvil.protocol.worker.TestOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The movement of one test client at a time, bound with operations of its own, whose client the test ticks unless it
 * starts the client's own ticks; and the packets the tests of {@link ClientMovement} exchange with it.
 */
final class BoundMovement implements AutoCloseable {
	static final ClientboundGameEvent LOADING_STARTED = new ClientboundGameEvent(GameEventType.LEVEL_CHUNKS_LOAD_START,
			0.0F);

	private TestOperations operations = new TestOperations();
	private ClientMovement movement;
	private TestClient bound;

	/**
	 * Binds the movement of a client, whose operations replace those of the movement bound before.
	 */
	@NotNull TestClient bind(@NotNull TestClient client) {
		operations = new TestOperations();
		movement = ClientMovement.bind(client.player, operations);
		bound = client;
		return client;
	}

	/**
	 * Binds the movement of a client that joined with its chunks sent, and turns its physics on.
	 */
	@NotNull TestClient physics(@NotNull TestClient client) {
		bind(client).join().send();
		assertEquals(new PhysicsChange(null), call(MovementChannel.PHYSICS, true));
		return client;
	}

	/**
	 * Brings the player into its level as a server's join does: the event that starts the level's loading, the teleport,
	 * then the client tick whose frame compiles the player's section, after which the player ticks.
	 */
	void enter(@NotNull TestClient client, @NotNull Vec3d at) {
		client.player.deliver(LOADING_STARTED);
		client.player.deliver(teleport(at, 0.0F, 0.0F, Set.of()));
		ticks(1);
	}

	<Q, R> @Nullable R call(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
		return operations.call(operation, request);
	}

	void ticks(int count) {
		for (int tick = 0; tick < count; tick++) bound.player.tick();
	}

	@NotNull MovementState state() {
		return call(MovementChannel.STATE, null);
	}

	@Override
	public void close() {
		if (movement != null) movement.close();
		if (bound != null) bound.player.close();
	}

	/**
	 * Loads the column of the test's blocks and the eight around it, as a section compiles only with them.
	 */
	static @NotNull TestClient area(@NotNull TestClient client) {
		for (int x = -1; x <= 1; x++)
			for (int z = -1; z <= 1; z++) client.load(x, z);
		return client;
	}

	static @NotNull ClientboundPlayerPosition teleport(
			@NotNull Vec3d position,
			float yaw,
			float pitch,
			@NotNull Set<Relative> relatives
	) {
		return ClientboundPlayerPosition.builder().position(position).yaw(yaw).pitch(pitch).relatives(relatives).build();
	}

	static @NotNull ServerboundAcceptTeleportation acceptance(double x, double y, double z, float yaw, float pitch) {
		return ServerboundAcceptTeleportation.builder().position(new Vec3d(x, y, z)).yaw(yaw).pitch(pitch).build();
	}

	static @NotNull ServerboundMovePlayerPosRot confirmation(double x, double y, double z, float yaw, float pitch) {
		return ServerboundMovePlayerPosRot.builder().position(new Vec3d(x, y, z)).yaw(yaw).pitch(pitch).build();
	}
}
