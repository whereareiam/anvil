package me.whereareiam.anvil.testkit.physics.harness.v1_19;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.Vec3;

/**
 * What the vanilla client of Minecraft 1.19.4 names differently from the other releases of its family, compiled
 * against that client alone.
 */
final class V1_19Release {
	private V1_19Release() {
	}

	/**
	 * The server's vanilla data pack, which its source keeps.
	 */
	static PackResources vanillaPack() {
		return new ServerPacksSource().getVanillaPack();
	}


	/**
	 * Adds the player to its level, through {@code addPlayer}.
	 */
	static void addPlayer(ClientLevel level, LocalPlayer player) {
		level.addPlayer(player.getId(), player);
	}

	/**
	 * Whether the player stands on the ground.
	 */
	static boolean onGround(LocalPlayer player) {
		return player.isOnGround();
	}

	/**
	 * Whether {@code FlowingFluid.isSolidFace} takes the block for ice, whose faces it never counts: its material is {@code Material.ICE}.
	 */
	static boolean isIce(BlockState state) {
		return state.getMaterial() == Material.ICE;
	}

	/**
	 * Whether {@code FlowingFluid.getFlow} takes the block for one that stops the flow beside it: its material blocks motion.
	 */
	static boolean blocksFluidFlow(BlockState state) {
		return state.getMaterial().blocksMotion();
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
