package me.whereareiam.anvil.testkit.physics.harness.v1_21_11;

import me.whereareiam.anvil.testkit.physics.harness.block.BlockProbe;
import me.whereareiam.anvil.testkit.physics.harness.block.MovementProbe;
import me.whereareiam.anvil.testkit.physics.harness.block.ShapeProbe;
import me.whereareiam.anvil.testkit.physics.harness.block.VanillaProperty;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the registered blocks and their states of the vanilla client of Minecraft 1.21.11 from its registries.
 *
 * <p>Its {@link V1_21_11BlockShapes} reads their shapes and its {@link V1_21_11BlockMovement} what they do to a moving
 * entity.</p>
 */
final class V1_21_11Blocks implements BlockProbe<Block, BlockState> {
	private final V1_21_11BlockShapes shapes;
	private final V1_21_11BlockMovement movement;

	V1_21_11Blocks(ClientLevel level, ProbePlayer player) {
		this.shapes = new V1_21_11BlockShapes(player);
		this.movement = new V1_21_11BlockMovement(level, player);
	}

	@Override
	public int protocol() {
		return SharedConstants.getProtocolVersion();
	}

	@Override
	public int stateCount() {
		return Block.BLOCK_STATE_REGISTRY.size();
	}

	@Override
	public List<Block> blocks() {
		List<Block> blocks = new ArrayList<>();
		for (Block block : BuiltInRegistries.BLOCK)
			blocks.add(block);
		return blocks;
	}

	@Override
	public String name(Block block) {
		return BuiltInRegistries.BLOCK.getKey(block).toString();
	}

	@Override
	public int blockId(Block block) {
		return BuiltInRegistries.BLOCK.getId(block);
	}

	@Override
	public List<BlockState> states(Block block) {
		return block.getStateDefinition().getPossibleStates();
	}

	@Override
	public int stateId(BlockState state) {
		return Block.getId(state);
	}

	@Override
	public BlockState defaultState(Block block) {
		return block.defaultBlockState();
	}

	@Override
	public List<VanillaProperty> properties(Block block) {
		List<VanillaProperty> properties = new ArrayList<>();
		for (Property<?> property : block.getStateDefinition().getProperties())
			properties.add(new VanillaProperty(property.getName(), valueNames(property)));
		return properties;
	}

	private static <T extends Comparable<T>> List<String> valueNames(Property<T> property) {
		List<String> names = new ArrayList<>();
		for (T value : property.getPossibleValues())
			names.add(property.getName(value));
		return names;
	}

	@Override
	public Map<String, String> values(BlockState state) {
		Map<String, String> values = new LinkedHashMap<>();
		for (Property<?> property : state.getProperties())
			values.put(property.getName(), valueName(state, property));
		return values;
	}

	private static <T extends Comparable<T>> String valueName(BlockState state, Property<T> property) {
		return property.getName(state.getValue(property));
	}

	@Override
	public ShapeProbe<Block, BlockState> shapes() {
		return shapes;
	}

	@Override
	public MovementProbe<Block, BlockState> movement() {
		return movement;
	}
}
