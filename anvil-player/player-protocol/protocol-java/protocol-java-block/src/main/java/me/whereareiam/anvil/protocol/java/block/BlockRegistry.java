package me.whereareiam.anvil.protocol.java.block;

import me.whereareiam.anvil.protocol.java.block.model.BlockDataSource;
import me.whereareiam.anvil.protocol.java.block.model.BlockState;
import me.whereareiam.anvil.protocol.java.block.model.BlockType;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The blocks and block states of one protocol, which every Minecraft version speaking it shares, looked up by global
 * block state ID as chunk and block update packets carry it.
 *
 * <p>Global IDs run from {@code 0} to {@link #stateCount()} minus one without gaps; a direct chunk palette needs
 * {@code ceil(log2(stateCount()))} bits per entry. {@link JavaBlockData} creates registries; they are immutable and
 * safe to share between threads.</p>
 *
 * <pre>{@code
 * BlockRegistry registry = JavaBlockData.shipped().require(767);
 * BlockState state = registry.state(stateId);
 * if (!state.isAir() && state.getCollision().isFullBlock())
 *     System.out.println(state.getBlock().getName() + " is a full block");
 * }</pre>
 */
public final class BlockRegistry {
	private final int protocol;
	private final @NotNull BlockDataSource source;
	private final @NotNull List<BlockType> blocks;
	private final @NotNull Map<String, BlockType> blocksByName;
	private final @NotNull BlockState @NotNull [] states;

	BlockRegistry(
			int protocol,
			@NotNull BlockDataSource source,
			@NotNull List<BlockType> blocks,
			@NotNull BlockState @NotNull [] states
	) {
		requireNumbered(protocol, blocks, states);
		this.protocol = protocol;
		this.source = source;
		this.blocks = List.copyOf(blocks);
		this.blocksByName = blocks.stream().collect(Collectors.toUnmodifiableMap(BlockType::getName, Function.identity()));
		this.states = states;
	}

	/**
	 * Checks what chunk packets rely on: blocks are numbered by their position, their states continue without gaps
	 * from {@code 0}, and each state is one of its block's at its own ID.
	 */
	private static void requireNumbered(int protocol, List<BlockType> blocks, BlockState[] states) {
		int next = 0;
		for (int id = 0; id < blocks.size(); id++) {
			BlockType block = blocks.get(id);
			if (block.getId() != id || block.getMinStateId() != next || block.getMaxStateId() < next)
				throw new IllegalArgumentException("Protocol " + protocol + " numbers " + block.getName() + " out of order");
			next = block.getMaxStateId() + 1;
		}
		if (next != states.length)
			throw new IllegalArgumentException(
					"Protocol " + protocol + " has " + states.length + " states for " + next + " state IDs");

		for (int id = 0; id < states.length; id++) {
			BlockType block = states[id].getBlock();
			if (states[id].getId() != id || id < block.getMinStateId() || id > block.getMaxStateId())
				throw new IllegalArgumentException("Protocol " + protocol + " misplaces state " + id);
		}
	}

	/**
	 * The protocol number the registry serves.
	 *
	 * @return the protocol, such as {@code 767}
	 */
	public int protocol() {
		return protocol;
	}

	/**
	 * Where the data comes from: the vanilla client it was read from.
	 *
	 * @return the data's source
	 */
	public @NotNull BlockDataSource source() {
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
	 * @throws IllegalArgumentException when the protocol has no state with that ID
	 */
	public @NotNull BlockState state(int stateId) {
		if (stateId < 0 || stateId >= states.length)
			throw new IllegalArgumentException("Protocol " + protocol + " has no block state " + stateId
					+ "; its state IDs run from 0 to " + (states.length - 1));

		return states[stateId];
	}

	/**
	 * Tests whether a global state ID is an air state.
	 *
	 * @param stateId global block state ID
	 * @return whether the state is {@code air}, {@code cave_air} or {@code void_air}
	 * @throws IllegalArgumentException when the protocol has no state with that ID
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
			throw new IllegalArgumentException(block.getName() + " is not a block of protocol " + protocol);

		return List.of(Arrays.copyOfRange(states, block.getMinStateId(), block.getMaxStateId() + 1));
	}

	/**
	 * The blocks of the protocol, in block ID order.
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
	 * @return the block, or empty when the protocol has no block of that name
	 */
	public @NotNull Optional<BlockType> block(@NotNull String name) {
		return Optional.ofNullable(blocksByName.get(name));
	}
}
