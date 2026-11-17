package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerSimulation;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.type.player.GameMode;
import me.whereareiam.anvil.protocol.java.block.BlockRegistry;
import me.whereareiam.anvil.protocol.java.physics.TestWorld;
import me.whereareiam.anvil.protocol.java.physics.geometry.Vector;
import me.whereareiam.anvil.protocol.java.worker.TestJavaPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.function.Predicate;

/**
 * A connected test player of one client version that joined a level of the physics kit's {@link TestWorld}, with the
 * server's state of its player and simulations of it in that world, as client physics starts them.
 */
public final class TestClient {
	public static final EntityId ENTITY_ID = TestWorld.ENTITY_ID;

	public final @NotNull MinecraftVersion version;
	public final @NotNull TestJavaPlayer player;
	public final @NotNull BlockRegistry registry;
	public final @NotNull DimensionType dimension;
	private final @NotNull TestWorld world;

	/**
	 * Creates a disconnected player whose world keeps chunks; {@link #join()} connects it.
	 */
	public TestClient(@NotNull String version) {
		this(new TestWorld(version));
	}

	/**
	 * Creates a disconnected player whose release carries only some packets.
	 */
	public TestClient(@NotNull String version, @NotNull Predicate<Class<? extends JavaPacket>> carried) {
		this(new TestWorld(version, carried));
	}

	/**
	 * Creates a disconnected player that joins a level of the given dimension type.
	 */
	public TestClient(@NotNull String version, @NotNull DimensionType dimension) {
		this(new TestWorld(version, dimension));
	}

	private TestClient(@NotNull TestWorld world) {
		this.version = world.version;
		this.player = world.player;
		this.registry = world.registry;
		this.dimension = world.dimension;
		this.world = world;
	}

	public @NotNull TestClient join() {
		world.join();
		return this;
	}

	public @NotNull TestClient load(int chunkX, int chunkZ) {
		world.load(chunkX, chunkZ);
		return this;
	}

	public @NotNull TestClient set(int x, int y, int z, @NotNull String block, @NotNull Map<String, String> properties) {
		world.set(x, y, z, block, properties);
		return this;
	}

	public @NotNull TestClient set(int x, int y, int z, @NotNull String block) {
		world.set(x, y, z, block);
		return this;
	}

	public @NotNull TestClient state(int x, int y, int z, int state) {
		world.state(x, y, z, state);
		return this;
	}

	public @NotNull TestClient floor(int fromX, int toX, int y, int fromZ, int toZ, @NotNull String block) {
		world.floor(fromX, toX, y, fromZ, toZ, block);
		return this;
	}

	public @NotNull TestClient send() {
		world.send();
		return this;
	}

	/**
	 * Starts a simulation of the player in this world, as client physics does.
	 */
	public @NotNull PlayerSimulation simulation(@NotNull PlayerState state, double x, double y, double z) {
		Position at = Position.builder().x(x).y(y).z(z).build();
		return PlayerSimulation.start(new MovementRules(version), state, world.reader(), at, Vector.ZERO);
	}

	/**
	 * Returns the server state of a player that joined this client's level as entity {@link #ENTITY_ID}.
	 */
	public @NotNull PlayerState state(@NotNull GameMode gameMode) {
		PlayerState state = new PlayerState(version);
		state.login(ClientboundLogin.builder().entityId(ENTITY_ID).level(TestWorld.OVERWORLD)
				.dimension(TestWorld.OVERWORLD).dimensionType(dimension).gameMode(gameMode).build());
		return state;
	}

	/**
	 * Starts a simulation of the player in this world with the server state of a fresh survival player.
	 */
	public @NotNull PlayerSimulation simulation(double x, double y, double z) {
		return simulation(new PlayerState(version), x, y, z);
	}
}
