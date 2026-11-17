package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * What the vanilla client of Minecraft 1.20.6 names differently from the other releases of its family, compiled
 * against that client alone.
 */
final class V1_19Release {
	private V1_19Release() {
	}

	/**
	 * The server's vanilla data pack.
	 */
	static PackResources vanillaPack() {
		return ServerPacksSource.createVanillaPackSource();
	}


	/**
	 * Adds the player to its level, through {@code addEntity}.
	 */
	static void addPlayer(ClientLevel level, LocalPlayer player) {
		level.addEntity(player);
	}

	/**
	 * Whether the player stands on the ground.
	 */
	static boolean onGround(LocalPlayer player) {
		return player.onGround();
	}

	/**
	 * Whether {@code FlowingFluid.isSolidFace} takes the block for ice, whose faces it never counts: an {@code IceBlock}.
	 */
	static boolean isIce(BlockState state) {
		return state.getBlock() instanceof IceBlock;
	}

	/**
	 * Whether {@code FlowingFluid.getFlow} takes the block for one that stops the flow beside it: the state blocks motion ({@code BlockState.blocksMotion}).
	 */
	static boolean blocksFluidFlow(BlockState state) {
		return state.blocksMotion();
	}

	/**
	 * The player {@link V1_19Blocks} reads blocks for: a local player whose sneaking the probe sets, as the input would,
	 * which stands in the state the probe names, as its level would hold it there, and which remembers the speed factors
	 * a block last made it stuck by.
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
