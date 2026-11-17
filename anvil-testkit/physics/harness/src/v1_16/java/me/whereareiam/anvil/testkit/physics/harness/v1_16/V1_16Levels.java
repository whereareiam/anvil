package me.whereareiam.anvil.testkit.physics.harness.v1_16;

import me.whereareiam.anvil.testkit.physics.harness.Course;
import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.Map;
import java.util.Optional;

/**
 * Creates the overworld levels of the harness: a level whose chunks around the origin are loaded, as the chunk packets
 * of a server would load them, holding a course's blocks.
 */
final class V1_16Levels {
	private V1_16Levels() {
	}

	/**
	 * Creates a level of the overworld whose columns within {@code radius} chunks of the origin are loaded and empty, as
	 * a chunk packet without blocks loads them, stored where the chunk cache keeps the chunks it receives.
	 */
	static ClientLevel loaded(ClientPacketListener listener, RegistryAccess registries, HeadlessLevelRenderer renderer, int radius) {
		ClientLevel level = V1_16Release.level(listener, registries, radius, renderer);
		ClientChunkCache.Storage storage = level.getChunkSource().storage;
		for (int x = -radius; x <= radius; x++)
			for (int z = -radius; z <= radius; z++) {
				storage.chunks.set(storage.getIndex(x, z), V1_16Release.chunk(level, new ChunkPos(x, z)));
				V1_16Client.chunkLoaded(level, x, z);
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
						level.getChunkAt(position).setBlockState(position, state, false);
					}
		}
	}

	private static BlockState state(Course.Box box) {
		ResourceLocation id = ResourceLocation.tryParse(box.getState().getBlock());
		Optional<Block> block = id == null ? Optional.empty() : Registry.BLOCK.getOptional(id);
		if (!block.isPresent())
			throw new IllegalArgumentException("Minecraft " + SharedConstants.getCurrentVersion().getName() + " has no block " + box.getState().getBlock());
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
		if (!parsed.isPresent())
			throw new IllegalArgumentException(box.getState() + ": " + name + " cannot be " + value);
		return state.setValue(property, parsed.get());
	}
}
