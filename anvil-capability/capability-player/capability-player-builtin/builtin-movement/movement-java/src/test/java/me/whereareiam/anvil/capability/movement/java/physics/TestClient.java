package me.whereareiam.anvil.capability.movement.java.physics;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Vector;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerState;
import me.whereareiam.anvil.capability.movement.java.physics.simulation.PlayerSimulation;
import me.whereareiam.anvil.capability.movement.java.physics.world.BlockReader;
import me.whereareiam.anvil.capability.movement.model.Position;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.ChunkPosition;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.LevelChunkSection;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundClientTickEnd;
import me.whereareiam.anvil.protocol.java.api.packet.player.ServerboundPlayerInput;
import me.whereareiam.anvil.protocol.java.api.packet.world.chunk.ClientboundLevelChunkWithLight;
import me.whereareiam.anvil.protocol.java.api.type.player.GameMode;
import me.whereareiam.anvil.protocol.java.block.BlockRegistry;
import me.whereareiam.anvil.protocol.java.block.JavaBlockData;
import me.whereareiam.anvil.protocol.java.block.model.BlockState;
import me.whereareiam.anvil.protocol.java.block.model.BlockType;
import me.whereareiam.anvil.protocol.java.worker.TestJavaPlayer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A connected test player of one client version that joined a level, whose world the test builds block by block with
 * the real block data of the version's protocol and sends as chunk packets, as a server would. Releases before 1.21.2
 * carry neither the held keys nor the tick end, as their protocols do not.
 */
public final class TestClient {
	public static final EntityId ENTITY_ID = new EntityId(7);
	private static final Map<String, Integer> PROTOCOLS = Map.of(
			"1.16.5", 754, "1.17.1", 756, "1.18.2", 758, "1.19.4", 762, "1.20.6", 766,
			"1.21.1", 767, "1.21.11", 774, "26.1.2", 775, "26.2", 776, "26.3", 777);
	private static final String OVERWORLD = "minecraft:overworld";

	public final @NotNull MinecraftVersion version;
	public final @NotNull TestJavaPlayer player;
	public final @NotNull BlockRegistry registry;
	public final @NotNull DimensionType dimension;
	private final @NotNull Map<List<Integer>, Integer> blocks = new HashMap<>();
	private final @NotNull Set<ChunkPosition> loaded = new LinkedHashSet<>();

	/**
	 * Creates a disconnected player whose world keeps chunks; {@link #join()} connects it.
	 */
	public TestClient(@NotNull String version) {
		this(version, carried(version));
	}

	/**
	 * Creates a disconnected player whose release carries only some packets.
	 */
	public TestClient(@NotNull String version, @NotNull Predicate<Class<? extends JavaPacket>> carried) {
		this(version, carried, MinecraftVersion.parse(version).isAtLeast(MinecraftVersion.parse("1.18"))
				? DimensionType.known(OVERWORLD)
				: DimensionType.withEffects(OVERWORLD, OVERWORLD, 0, 256));
	}

	/**
	 * Creates a disconnected player that joins a level of the given dimension type.
	 */
	public TestClient(@NotNull String version, @NotNull DimensionType dimension) {
		this(version, carried(version), dimension);
	}

	private TestClient(
			@NotNull String version,
			@NotNull Predicate<Class<? extends JavaPacket>> carried,
			@NotNull DimensionType dimension
	) {
		this.version = MinecraftVersion.parse(version);
		int protocol = PROTOCOLS.get(version);
		this.player = new TestJavaPlayer("Alice", this.version, protocol, carried);
		this.registry = JavaBlockData.shipped().find(protocol).orElse(JavaBlockData.shipped().require(774));
		this.dimension = dimension;
		player.protocol(JavaProtocol.class).world();
	}

	/**
	 * Connects the player and lets it join the overworld as entity {@link #ENTITY_ID}.
	 */
	public @NotNull TestClient join() {
		player.connect();
		player.deliver(ClientboundLogin.builder()
				.entityId(ENTITY_ID)
				.level(OVERWORLD)
				.dimension(OVERWORLD)
				.dimensionType(dimension)
				.build());
		return this;
	}

	/**
	 * Names the packets the release of a version carries: the held keys and the tick end only from 1.21.2.
	 */
	private static @NotNull Predicate<Class<? extends JavaPacket>> carried(@NotNull String version) {
		boolean current = MinecraftVersion.parse(version).isAtLeast(MinecraftVersion.parse("1.21.2"));
		return type -> current || type != ServerboundPlayerInput.class && type != ServerboundClientTickEnd.class;
	}

	public @NotNull TestClient load(int chunkX, int chunkZ) {
		loaded.add(new ChunkPosition(chunkX, chunkZ));
		return this;
	}

	/**
	 * Sets a block in the state whose properties have the given values.
	 */
	public @NotNull TestClient set(int x, int y, int z, @NotNull String block, @NotNull Map<String, String> properties) {
		BlockType type = registry.block(block).orElseThrow();
		BlockState state = registry.states(type).stream()
				.filter(candidate -> candidate.getProperties().entrySet().containsAll(properties.entrySet()))
				.findFirst()
				.orElseThrow();
		return state(x, y, z, state.getId());
	}

	public @NotNull TestClient set(int x, int y, int z, @NotNull String block) {
		return state(x, y, z, registry.block(block).orElseThrow().getDefaultStateId());
	}

	public @NotNull TestClient state(int x, int y, int z, int state) {
		blocks.put(List.of(x, y, z), state);
		return this;
	}

	public @NotNull TestClient floor(int fromX, int toX, int y, int fromZ, int toZ, @NotNull String block) {
		for (int x = fromX; x <= toX; x++)
			for (int z = fromZ; z <= toZ; z++) set(x, y, z, block);
		return this;
	}

	/**
	 * Sends every loaded column with the blocks set so far, as chunk packets.
	 */
	public @NotNull TestClient send() {
		for (ChunkPosition chunk : loaded) player.deliver(new ClientboundLevelChunkWithLight(chunk, sections(chunk)));
		return this;
	}

	/**
	 * Starts a simulation of the player in this world, as client physics does.
	 */
	public @NotNull PlayerSimulation simulation(@NotNull PlayerState state, double x, double y, double z) {
		BlockReader reader = new BlockReader(player.protocol(JavaProtocol.class).world(), registry);
		Position at = Position.builder().x(x).y(y).z(z).build();
		return PlayerSimulation.start(new MovementRules(version), state, reader, at, Vector.ZERO);
	}

	/**
	 * Returns the server state of a player that joined this client's level as entity {@link #ENTITY_ID}.
	 */
	public @NotNull PlayerState state(@NotNull GameMode gameMode) {
		PlayerState state = new PlayerState(version);
		state.login(ClientboundLogin.builder().entityId(ENTITY_ID).level(OVERWORLD).dimension(OVERWORLD)
				.dimensionType(dimension).gameMode(gameMode).build());
		return state;
	}

	/**
	 * Starts a simulation of the player in this world with the server state of a fresh survival player.
	 */
	public @NotNull PlayerSimulation simulation(double x, double y, double z) {
		return simulation(new PlayerState(version), x, y, z);
	}

	private @NotNull List<LevelChunkSection> sections(@NotNull ChunkPosition chunk) {
		List<LevelChunkSection> sections = new ArrayList<>();
		for (int section = 0; section < dimension.getHeight() >> 4; section++)
			sections.add(section(chunk, dimension.getMinY() + section * 16));
		return sections;
	}

	private @NotNull LevelChunkSection section(@NotNull ChunkPosition chunk, int bottom) {
		int[] states = new int[4096];
		List<Integer> palette = new ArrayList<>(List.of(LevelChunkSection.AIR));
		for (int y = 0; y < 16; y++)
			for (int z = 0; z < 16; z++)
				for (int x = 0; x < 16; x++) {
					int state = blocks.getOrDefault(List.of(chunk.getX() * 16 + x, bottom + y, chunk.getZ() * 16 + z), 0);
					if (!palette.contains(state)) palette.add(state);
					states[(y << 8) | (z << 4) | x] = palette.indexOf(state);
				}
		if (palette.size() == 1) return LevelChunkSection.EMPTY;

		int bits = Math.max(4, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
		int perLong = 64 / bits;
		long[] data = new long[(4096 + perLong - 1) / perLong];
		for (int index = 0; index < 4096; index++)
			data[index / perLong] |= (long) states[index] << (index % perLong * bits);
		return LevelChunkSection.paletted(palette.stream().mapToInt(Integer::intValue).toArray(), bits, data);
	}
}
