package me.whereareiam.anvil.testkit.physics.harness.v26;

import me.whereareiam.anvil.testkit.physics.harness.Course;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;

import java.util.Map;
import java.util.Optional;

/**
 * Creates the overworld levels of the harness: a level whose chunks around the origin are loaded, as the chunk packets
 * of a server would load them, holding a course's blocks.
 */
final class V26Levels {
	private V26Levels() {
	}

	/**
	 * The overworld's dimension type.
	 */
	static Holder<DimensionType> overworld(RegistryAccess registries) {
		return registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
	}

	/**
	 * Creates a level of the overworld whose columns within {@code radius} chunks of the origin are loaded and empty, as
	 * a chunk packet without blocks loads them, stored where the chunk cache keeps the chunks it receives.
	 */
	static ClientLevel loaded(ClientPacketListener listener, RegistryAccess registries, HeadlessRendering rendering, int radius) {
		ClientLevel level = V26Client.level(listener, overworld(registries), radius, rendering);
		ClientChunkCache.Storage storage = level.getChunkSource().storage;
		for (int x = -radius; x <= radius; x++)
			for (int z = -radius; z <= radius; z++) {
				storage.chunks.set(storage.getIndex(x, z), new LevelChunk(level, new ChunkPos(x, z)));
				level.onChunkLoaded(new ChunkPos(x, z));
			}
		return level;
	}

	/**
	 * Lays the course's boxes of blocks in order.
	 */
	static void lay(ClientLevel level, Course course) {
		for (Course.Box box : course.getBlocks()) {
			BlockState state = state(box);
			for (int x = box.getMinX(); x <= box.getMaxX(); x++)
				for (int y = box.getMinY(); y <= box.getMaxY(); y++)
					for (int z = box.getMinZ(); z <= box.getMaxZ(); z++) {
						BlockPos position = new BlockPos(x, y, z);
						level.getChunkAt(position).setBlockState(position, state, 0);
					}
		}
	}

	private static BlockState state(Course.Box box) {
		Identifier id = Identifier.tryParse(box.getState().getBlock());
		Optional<Block> block = id == null ? Optional.empty() : BuiltInRegistries.BLOCK.getOptional(id);
		if (block.isEmpty())
			throw new IllegalArgumentException("Minecraft " + SharedConstants.getCurrentVersion().name() + " has no block " + box.getState().getBlock());
		BlockState state = block.get().defaultBlockState();
		for (Map.Entry<String, String> entry : box.getState().getProperties().entrySet())
			state = with(state, block.get(), entry.getKey(), entry.getValue(), box);
		return state;
	}

	private static <T extends Comparable<T>> BlockState with(BlockState state, Block block, String name, String value, Course.Box box) {
		@SuppressWarnings("unchecked")
		Property<T> property = (Property<T>) block.getStateDefinition().getProperty(name);
		if (property == null)
			throw new IllegalArgumentException(box.getState() + ": the block has no property " + name);
		Optional<T> parsed = property.getValue(value);
		if (parsed.isEmpty())
			throw new IllegalArgumentException(box.getState() + ": " + name + " cannot be " + value);
		return state.setValue(property, parsed.get());
	}
}
