package me.whereareiam.anvil.testkit.physics.harness.v26;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * What the vanilla client of Minecraft 26.1.2 names differently from the other releases of its family, compiled
 * against that client alone.
 */
final class V26Release {
	private V26Release() {
	}

	/**
	 * The layer of the world's registries, {@code WORLDGEN} until 26.3.
	 */
	static RegistryLayer worldLayer() {
		return RegistryLayer.WORLDGEN;
	}

	/**
	 * The registries the world loads from the data pack, {@code WORLDGEN_REGISTRIES} until 26.3.
	 */
	static List<RegistryDataLoader.RegistryData<?>> worldRegistries() {
		return RegistryDataLoader.WORLDGEN_REGISTRIES;
	}

	/**
	 * The resources of the server's vanilla data pack, the vanilla pack itself.
	 */
	static ResourceManager vanillaResources() {
		return new MultiPackResourceManager(PackType.SERVER_DATA, List.of(ServerPacksSource.createVanillaPackSource()));
	}


	/**
	 * Creates the player as the login's game mode does.
	 */
	static LocalPlayer createPlayer(MultiPlayerGameMode gameMode, ClientLevel level) {
		return gameMode.createPlayer(level, new StatsCounter(), new ClientRecipeBook());
	}

	/**
	 * Runs what {@code Minecraft.tick} runs for the player once the level ticked its entities: nothing until 26.3, whose player sends its changes from its own tick.
	 */
	static void afterEntityTick(LocalPlayer player) {
		// The player sent its changes from its own tick, which the level already ran.
	}

	/**
	 * How much of its fall speed a living entity that does not suppress bouncing keeps, reversed, landing on the block:
	 * what its {@code updateEntityMovementAfterFallOn} leaves of a player's downward speed of {@code 1.0}.
	 */
	static double bounce(Block block, ProbePlayer player) {
		player.descending = false;
		player.setDeltaMovement(0.0, -1.0, 0.0);
		block.updateEntityMovementAfterFallOn(EmptyBlockGetter.INSTANCE, player);
		return player.getDeltaMovement().y;
	}

	/**
	 * Whether {@code FlowingFluid.getFlow} takes the block for one that stops the flow beside it: the state blocks motion ({@code BlockState.blocksMotion}).
	 */
	static boolean blocksFluidFlow(BlockState state) {
		return state.blocksMotion();
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
			super(minecraft, level, listener, new StatsCounter(), new ClientRecipeBook(), Input.EMPTY, false, minecraft.computeChatAbilities());
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
