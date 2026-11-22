package me.whereareiam.anvil.testkit.physics.harness.v1_16;

import net.minecraft.SharedConstants;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.game.ClientboundUpdateTagsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.stats.StatsCounter;
import net.minecraft.tags.Tag;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagManager;
import net.minecraft.tags.TagNetworkSerialization;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * What the vanilla client of Minecraft 1.18.2 names differently from the other releases of its family, compiled
 * against that client alone.
 */
final class V1_16Release {
	private static final Executor DIRECT = Runnable::run;

	private V1_16Release() {
	}

	/**
	 * Detects the game's version as the client's main class does before it bootstraps.
	 */
	static void detectVersion() {
		SharedConstants.tryDetectVersion();
	}

	/**
	 * The game's built-in registry access, which the client's registries are until a server sends its own, frozen.
	 */
	static RegistryAccess builtinRegistries() {
		return RegistryAccess.BUILTIN.get();
	}

	/**
	 * Loads the tags of the vanilla data pack, as the server does, and binds them as the client binds the tags the
	 * server sends, to the registries' holders.
	 *
	 * @return the tags packet the server sends a player that joins, made before any login resets the client's tags
	 */
	static ClientboundUpdateTagsPacket bindTags(ResourceManager resources, RegistryAccess registries) throws Exception {
		TagManager tags = new TagManager(registries);
		PreparableReloadListener.PreparationBarrier barrier = new PreparableReloadListener.PreparationBarrier() {
			@Override
			public <T> CompletableFuture<T> wait(T value) {
				return CompletableFuture.completedFuture(value);
			}
		};
		tags.reload(barrier, resources, InactiveProfiler.INSTANCE, InactiveProfiler.INSTANCE, DIRECT, DIRECT).get();
		for (TagManager.LoadResult<?> result : tags.getResult())
			bind(registries, result);
		Blocks.rebuildCache();
		return new ClientboundUpdateTagsPacket(TagNetworkSerialization.serializeTagsToNetwork(registries));
	}

	private static <T> void bind(RegistryAccess registries, TagManager.LoadResult<T> result) {
		Map<TagKey<T>, List<Holder<T>>> bound = new HashMap<>();
		for (Map.Entry<ResourceLocation, Tag<Holder<T>>> entry : result.tags().entrySet())
			bound.put(TagKey.create(result.key(), entry.getKey()), entry.getValue().getValues());
		registries.registryOrThrow(result.key()).bindTags(bound);
	}

	/**
	 * Creates a fresh level of the overworld that loads {@code radius} chunks around its center, of the dimension type's holder, which also takes the simulation distance.
	 */
	static ClientLevel level(ClientPacketListener listener, RegistryAccess registries, int radius, LevelRenderer renderer) {
		ClientLevel.ClientLevelData data = new ClientLevel.ClientLevelData(Difficulty.NORMAL, false, false);
		Holder<DimensionType> overworld = registries.registryOrThrow(Registry.DIMENSION_TYPE_REGISTRY).getHolderOrThrow(DimensionType.OVERWORLD_LOCATION);
		return new ClientLevel(listener, data, Level.OVERWORLD, overworld, radius, radius, () -> InactiveProfiler.INSTANCE, renderer, false, 0L);
	}

	/**
	 * Creates an empty column of the level, as a chunk packet without blocks loads it.
	 */
	static LevelChunk chunk(ClientLevel level, ChunkPos position) {
		return new LevelChunk(level, position);
	}

	/**
	 * Whether the block is powder snow.
	 */
	static boolean isPowderSnow(Block block) {
		return block == Blocks.POWDER_SNOW;
	}

	/**
	 * Every block tag the block is in, by name, from its holder in the block registry.
	 */
	static Set<String> tags(Block block) {
		Set<String> tags = new HashSet<>();
		block.builtInRegistryHolder().tags().forEach(tag -> tags.add(tag.location().toString()));
		return tags;
	}

	/**
	 * The player {@link V1_16Blocks} reads blocks for: a local player whose sneaking the probe sets, as the input would,
	 * which stands in the state the probe names, as its level would hold it there, and which remembers the speed factors a block last made it stuck by.
	 */
	static final class ProbePlayer extends LocalPlayer {
		boolean descending;
		BlockState inside;
		Vec3 stuck;

		ProbePlayer(Minecraft minecraft, ClientLevel level, ClientPacketListener listener) {
			super(minecraft, level, listener, new StatsCounter(), new ClientRecipeBook(), false, false);
		}

		@Override
		public boolean isShiftKeyDown() {
			return descending;
		}

		@Override
		public BlockState getFeetBlockState() {
			return inside != null ? inside : super.getFeetBlockState();
		}

		@Override
		public void makeStuckInBlock(BlockState state, Vec3 speed) {
			super.makeStuckInBlock(state, speed);
			stuck = speed;
		}
	}
}
