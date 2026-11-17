package me.whereareiam.anvil.testkit.physics.harness.v26;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.ItemActivation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.stats.StatsCounter;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What the vanilla client of Minecraft 26.3 names differently from the other releases of its family, compiled
 * against that client alone.
 */
final class V26Release {
	private V26Release() {
	}

	/**
	 * The layer of the world's registries, {@code WORLD} from 26.3.
	 */
	static RegistryLayer worldLayer() {
		return RegistryLayer.WORLD;
	}

	/**
	 * The registries the world loads from the data pack, {@code WORLD_REGISTRIES} from 26.3.
	 */
	static List<RegistryDataLoader.RegistryData<?>> worldRegistries() {
		return RegistryDataLoader.WORLD_REGISTRIES;
	}

	/**
	 * The resources of the server's vanilla data pack, its resource layers, opened as the server opens the selected {@code vanilla} pack.
	 */
	static ResourceManager vanillaResources() {
		return new MultiPackResourceManager(PackType.SERVER_DATA, vanillaRepository().openAllSelected());
	}

	private static PackRepository vanillaRepository() {
		PackRepository repository = ServerPacksSource.createVanillaTrustedRepository();
		repository.reload();
		repository.setSelected(List.of("vanilla"));
		return repository;
	}


	/**
	 * Creates the player as the login's game mode does, with the item activation the screen shows.
	 */
	static LocalPlayer createPlayer(MultiPlayerGameMode gameMode, ClientLevel level) {
		return gameMode.createPlayer(level, new StatsCounter(), new ClientRecipeBook(), new ItemActivation());
	}

	/**
	 * Runs what {@code Minecraft.tick} runs for the player once the level ticked its entities: from 26.3 the player sends its changes ({@code sendChanges}) only here, no longer from its own tick.
	 */
	static void afterEntityTick(LocalPlayer player) {
		if (!player.isRemoved())
			player.sendChanges();
	}

	/**
	 * How much of its fall speed a living entity that does not suppress bouncing keeps, reversed, landing on the block:
	 * its bounce restitution ({@code getBounceRestitution}), which 26.2 states as data.
	 */
	static double bounce(Block block, ProbePlayer player) {
		return block.getBounceRestitution();
	}

	/**
	 * Whether {@code FlowingFluid.getFlow} takes the block for one that stops the flow beside it: the state is in the {@code blocks_fluid_flow} tag.
	 */
	static boolean blocksFluidFlow(BlockState state) {
		return state.is(BlockTags.BLOCKS_FLUID_FLOW);
	}

	/**
	 * The player {@link V26Blocks} reads blocks for: a local player whose sneaking the probe sets, as the input would,
	 * which stands in the state the probe names, as its level would hold it there, and which remembers the speed factors
	 * a block last made it stuck by.
	 */
	static final class ProbePlayer extends LocalPlayer {
		boolean descending;
		BlockState inside;
		Vec3 stuck;

		ProbePlayer(Minecraft minecraft, ClientLevel level, ClientPacketListener listener) {
			super(minecraft, level, listener, new StatsCounter(), new ClientRecipeBook(), Input.EMPTY, false, minecraft.computeChatAbilities(), new ItemActivation());
		}

		@Override
		public boolean isShiftKeyDown() {
			return descending;
		}

		@Override
		public BlockState getInBlockState() {
			return inside != null ? inside : super.getInBlockState();
		}

		@Override
		public void makeStuckInBlock(BlockState state, Vec3 speed) {
			super.makeStuckInBlock(state, speed);
			stuck = speed;
		}
	}
}
