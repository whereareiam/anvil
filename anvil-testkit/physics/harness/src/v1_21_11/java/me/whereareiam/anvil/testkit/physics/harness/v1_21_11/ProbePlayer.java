package me.whereareiam.anvil.testkit.physics.harness.v1_21_11;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The player the probe reads blocks for: a local player whose sneaking the probe sets, as the input would, which
 * stands in the state the probe names, as its level would hold it there, and which remembers the speed factors a
 * block last made it stuck by.
 */
final class ProbePlayer extends LocalPlayer {
	boolean descending;
	BlockState inside;
	Vec3 stuck;

	ProbePlayer(Minecraft minecraft, ClientLevel level, ClientPacketListener listener) {
		super(minecraft, level, listener, new StatsCounter(), new ClientRecipeBook(), Input.EMPTY, false);
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
