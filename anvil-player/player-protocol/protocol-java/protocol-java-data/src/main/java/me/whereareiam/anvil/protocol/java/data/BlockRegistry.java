package me.whereareiam.anvil.protocol.java.data;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.java.data.model.BlockState;
import me.whereareiam.anvil.protocol.java.data.model.BlockType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The blocks and block states of the Minecraft versions that share one protocol, looked up by global block state ID
 * as chunk and block update packets carry it.
 *
 * <p>Global IDs run from {@code 0} to {@link #stateCount()} minus one without gaps; a direct chunk palette needs
 * {@code ceil(log2(stateCount()))} bits per entry. {@link JavaBlockData} creates registries; they are immutable and
 * safe to share between threads.</p>
 *
 * <pre>{@code
 * BlockRegistry registry = JavaBlockData.shipped().require(MinecraftVersion.parse("1.21.1"));
 * BlockState state = registry.state(stateId);
 * if (!state.isAir() && state.getCollision().isFullBlock())
 *     System.out.println(state.getBlock().getName() + " is a full block");
 * }</pre>
 */
public final class BlockRegistry {
	private final @NotNull List<MinecraftVersion> minecraftVersions;
	private final @NotNull String source;
	private final @NotNull List<BlockType> blocks;
	private final @NotNull Map<String, BlockType> blocksByName;
	private final @NotNull BlockState @NotNull [] states;

	BlockRegistry(
			@NotNull List<MinecraftVersion> minecraftVersions,
			@NotNull String source,
			@NotNull List<BlockType> blocks,
			@NotNull BlockState @NotNull [] states
	) {
		this.minecraftVersions = List.copyOf(minecraftVersions);
		this.source = source;
		this.blocks = List.copyOf(blocks);
		this.blocksByName = blocks.stream().collect(Collectors.toUnmodifiableMap(BlockType::getName, Function.identity()));
		this.states = states;
	}

	/**
	 * The Minecraft versions the registry serves, which speak one protocol.
	 *
	 * @return the versions, oldest first
	 */
	public @NotNull List<MinecraftVersion> minecraftVersions() {
		return minecraftVersions;
	}

	/**
	 * Where the data comes from: the minecraft-data repository, release, commit and files.
	 *
	 * @return a description of the data's source, such as
	 *         {@code PrismarineJS/minecraft-data 3.117.0 15411f0... data/pc/1.16.2/blocks.json ...}
	 */
	public @NotNull String source() {
		return source;
	}

	/**
	 * The number of block states, one more than the highest global state ID.
	 *
	 * @return the state count
	 */
	public int stateCount() {
		return states.length;
	}

	/**
	 * Returns a block state by its global ID.
	 *
	 * @param stateId global block state ID
	 * @return the state
	 * @throws IllegalArgumentException when the release has no state with that ID
	 */
	public @NotNull BlockState state(int stateId) {
		if (stateId < 0 || stateId >= states.length)
			throw new IllegalArgumentException("Minecraft " + minecraftVersions.getLast() + " has no block state " + stateId
					+ "; its state IDs run from 0 to " + (states.length - 1));

		return states[stateId];
	}

	/**
	 * Tests whether a global state ID is an air state.
	 *
	 * @param stateId global block state ID
	 * @return whether the state is {@code air}, {@code cave_air} or {@code void_air}
	 * @throws IllegalArgumentException when the release has no state with that ID
	 */
	public boolean isAir(int stateId) {
		return state(stateId).isAir();
	}

	/**
	 * Returns every state of a block, in global ID order.
	 *
	 * @param block block of this registry
	 * @return the block's states
	 * @throws IllegalArgumentException when the block is not this registry's
	 */
	public @NotNull List<BlockState> states(@NotNull BlockType block) {
		if (blocksByName.get(block.getName()) != block)
			throw new IllegalArgumentException(block.getName() + " is not a block of Minecraft " + minecraftVersions.getLast());

		return List.of(Arrays.copyOfRange(states, block.getMinStateId(), block.getMaxStateId() + 1));
	}

	/**
	 * The blocks of the release, in block ID order.
	 *
	 * @return every block
	 */
	public @NotNull List<BlockType> blocks() {
		return blocks;
	}

	/**
	 * Looks a block up by its namespaced name.
	 *
	 * @param name namespaced name, such as {@code minecraft:stone}
	 * @return the block, or empty when the release has no block of that name
	 */
	public @NotNull Optional<BlockType> block(@NotNull String name) {
		return Optional.ofNullable(blocksByName.get(name));
	}
}
