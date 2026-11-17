package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.capability.movement.channel.MovementChannel;
import me.whereareiam.anvil.capability.movement.channel.PhysicsChange;
import me.whereareiam.anvil.capability.movement.model.MovementInput;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.physics.living.LivingBody;
import me.whereareiam.anvil.protocol.worker.TestOperations;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Anvil's client of one release as the reference traces drive it: the movement of a connected test player that joined
 * the level, with physics on, whose ticks the trace runs one by one, and the packets it sent since the last look.
 */
public final class ReferenceClient implements AutoCloseable {
	private final @NotNull TestClient client;
	private final @NotNull TestOperations operations = new TestOperations();
	private final @NotNull ClientMovement movement;
	private int seen;

	private ReferenceClient(@NotNull TestClient client) {
		this.client = client;
		this.movement = ClientMovement.bind(client.player, operations);
	}

	/**
	 * Logs a player of a release into its level, as entity {@link TestClient#ENTITY_ID}, and turns its physics on.
	 *
	 * @param release the release's Minecraft version
	 * @return the client
	 */
	public static @NotNull ReferenceClient join(@NotNull String release) {
		ReferenceClient reference = new ReferenceClient(new TestClient(release));
		reference.client.join();
		PhysicsChange change = reference.operations.call(MovementChannel.PHYSICS, true);
		if (change == null || change.getRefusal() != null)
			throw new IllegalStateException("Client physics of " + release + " cannot run: " + change);
		return reference;
	}

	/**
	 * Returns the test player and its world.
	 */
	public @NotNull TestClient client() {
		return client;
	}

	/**
	 * Places the player at rest in a level the client has loaded, without the server's handshake.
	 */
	public void place(@NotNull Position at) {
		client.player.place(new Vec3d(at.getX(), at.getY(), at.getZ()), new ViewRotation(at.getYaw(), at.getPitch()),
				at.isOnGround());
	}

	/**
	 * Runs one client tick with the keys held.
	 */
	public void tick(@NotNull MovementInput keys) {
		operations.call(MovementChannel.INPUT, keys);
		client.player.tick();
	}

	/**
	 * Returns the simulated player's body, or {@code null} before the client knows where it is.
	 */
	public @Nullable LivingBody body() {
		return movement.body();
	}

	/**
	 * Returns the packets the client sent since the last call, in send order.
	 */
	public @NotNull List<ServerboundPacket> sent() {
		List<ServerboundPacket> all = client.player.sent();
		List<ServerboundPacket> news = List.copyOf(all.subList(seen, all.size()));
		seen = all.size();
		return news;
	}

	@Override
	public void close() {
		movement.close();
	}
}
