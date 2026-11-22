package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.java.physics.player.PlayerSimulation;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.edition.java.data.block.BlockRegistry;
import me.whereareiam.anvil.edition.java.physics.TestWorld;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.world.ClientboundGameEvent;
import me.whereareiam.anvil.edition.java.protocol.type.player.GameMode;
import me.whereareiam.anvil.edition.java.protocol.type.world.GameEventType;
import me.whereareiam.anvil.edition.java.protocol.worker.TestJavaPlayer;
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
	 * Creates a disconnected player of a client version that speaks the given protocol, such as one Anvil ships no
	 * block data of.
	 */
	public TestClient(@NotNull String version, int protocol) {
		this(new TestWorld(version, protocol));
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
		return simulation(state, Position.builder().x(x).y(y).z(z).build());
	}

	/**
	 * Starts a simulation of the player at rest where a server teleport placed it, as client physics does.
	 */
	public @NotNull PlayerSimulation simulation(@NotNull PlayerState state, @NotNull Position at) {
		return PlayerSimulation.start(new MovementRules(version), state, world.reader(), at, Vector.ZERO);
	}

	/**
	 * Puts the player, which joined this client's level as entity {@link #ENTITY_ID}, in a game mode, as the server's
	 * change of the game mode does, and returns the server state of a new player in it.
	 */
	public @NotNull PlayerState state(@NotNull GameMode gameMode) {
		player.deliver(new ClientboundGameEvent(GameEventType.CHANGE_GAME_MODE, gameMode.ordinal()));
		PlayerState state = new PlayerState(version, player.protocol().player());
		state.login();
		return state;
	}

	/**
	 * Starts a simulation of the player in this world with the server state of a new player.
	 */
	public @NotNull PlayerSimulation simulation(double x, double y, double z) {
		return simulation(new PlayerState(version, player.protocol().player()), x, y, z);
	}
}
