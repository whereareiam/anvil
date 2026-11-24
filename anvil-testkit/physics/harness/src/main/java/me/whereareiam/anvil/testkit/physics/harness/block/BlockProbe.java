package me.whereareiam.anvil.testkit.physics.harness.block;

import java.util.List;
import java.util.Map;

/**
 * Reads the blocks of a bootstrapped vanilla client from its own registries, classes and methods, for
 * {@link BlockTableReader}: what each family implements against its releases' client classes. This probe reads the
 * registered blocks and their states, its {@link #shapes()} their collision shapes and its {@link #movement()} what they
 * do to a moving entity. Every answer is the client's own; a probe decides nothing that the client does not state.
 *
 * @param <B> the client's block type
 * @param <S> the client's block state type
 */
public interface BlockProbe<B, S> {
	/**
	 * The protocol number the client speaks, {@code SharedConstants.getProtocolVersion}.
	 */
	int protocol();

	/**
	 * The number of block states, {@code Block.BLOCK_STATE_REGISTRY.size()}.
	 */
	int stateCount();

	/**
	 * Every block of the block registry, in block ID order.
	 */
	List<B> blocks();

	/**
	 * The block's namespaced name in the block registry, such as {@code minecraft:stone}.
	 */
	String name(B block);

	/**
	 * The block's ID in the block registry.
	 */
	int blockId(B block);

	/**
	 * The block's states in the order its state definition lists them.
	 */
	List<S> states(B block);

	/**
	 * The state's global ID, {@code Block.getId}, which chunk packets carry.
	 */
	int stateId(S state);

	/**
	 * The block's default state, {@code defaultBlockState}.
	 */
	S defaultState(B block);

	/**
	 * The block's properties in its state definition's order, each with its possible values in order.
	 */
	List<VanillaProperty> properties(B block);

	/**
	 * The value of each of the state's properties, by name, as {@code Property.getName} writes it.
	 */
	Map<String, String> values(S state);

	/**
	 * The probe of the blocks' collision shapes, offsets and sturdy faces.
	 */
	ShapeProbe<B, S> shapes();

	/**
	 * The probe of what the blocks do to a moving entity.
	 */
	MovementProbe<B, S> movement();
}
