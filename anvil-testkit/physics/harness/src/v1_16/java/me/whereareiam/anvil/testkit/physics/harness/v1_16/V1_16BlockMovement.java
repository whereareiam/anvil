package me.whereareiam.anvil.testkit.physics.harness.v1_16;

import me.whereareiam.anvil.testkit.physics.harness.block.BlockKind;
import me.whereareiam.anvil.testkit.physics.harness.block.MovementBehaviour;
import me.whereareiam.anvil.testkit.physics.harness.block.MovementProbe;
import me.whereareiam.anvil.testkit.physics.harness.block.VanillaFluid;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HoneyBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.Set;

/**
 * Reads what the blocks of the vanilla clients of Minecraft 1.16.5 to 1.18.2 do to a moving entity: their friction and
 * factors, the bounce and stuck speed they give a player, their fluids, and what vanilla's movement code asks of their
 * kind, tags and classes.
 */
final class V1_16BlockMovement implements MovementProbe<Block, BlockState> {
	/**
	 * Where the player stands in a state for its {@code entityInside}, in a loaded column of the probe's level.
	 */
	private static final BlockPos INSIDE = new BlockPos(0, 64, 0);

	private final ClientLevel level;
	private final V1_16Release.ProbePlayer player;

	V1_16BlockMovement(ClientLevel level, V1_16Release.ProbePlayer player) {
		this.level = level;
		this.player = player;
	}

	@Override
	public float friction(Block block) {
		return block.getFriction();
	}

	@Override
	public float speedFactor(Block block) {
		return block.getSpeedFactor();
	}

	@Override
	public float jumpFactor(Block block) {
		return block.getJumpFactor();
	}

	@Override
	public double bounce(Block block) {
		player.descending = false;
		player.setDeltaMovement(0.0, -1.0, 0.0);
		block.updateEntityAfterFallOn(EmptyBlockGetter.INSTANCE, player);
		return player.getDeltaMovement().y;
	}

	@Override
	public double[] stuck(BlockState state) {
		player.setPos(0.5, INSIDE.getY(), 0.5);
		player.descending = false;
		player.setDeltaMovement(Vec3.ZERO);
		player.inside = state;
		player.stuck = null;
		state.entityInside(level, INSIDE, player);
		player.inside = null;
		Vec3 stuck = player.stuck;
		return stuck == null ? null : new double[] {stuck.x, stuck.y, stuck.z};
	}

	@Override
	public VanillaFluid fluid(BlockState state) {
		FluidState fluid = state.getFluidState();
		if (fluid.isEmpty())
			return VanillaFluid.NONE;
		return new VanillaFluid(type(state, fluid), fluid.getAmount(), fluid.isSource(),
			fluid.getOptionalValue(FlowingFluid.FALLING).orElse(false));
	}

	private static VanillaFluid.Type type(BlockState state, FluidState fluid) {
		if (fluid.is(FluidTags.WATER))
			return VanillaFluid.Type.WATER;
		if (fluid.is(FluidTags.LAVA))
			return VanillaFluid.Type.LAVA;
		throw new IllegalStateException(state + " holds " + fluid + ", neither water nor lava");
	}

	@Override
	public boolean blocksFluidFlow(BlockState state) {
		return state.getMaterial().blocksMotion();
	}

	@Override
	public BlockKind kind(Block block) {
		BlockState state = block.defaultBlockState();
		if (state.isAir())
			return BlockKind.AIR;
		if (block instanceof FenceGateBlock)
			return BlockKind.FENCE_GATE;
		if (block instanceof TrapDoorBlock)
			return BlockKind.TRAPDOOR;
		if (block == Blocks.LADDER)
			return BlockKind.LADDER;
		if (block == Blocks.SCAFFOLDING)
			return BlockKind.SCAFFOLDING;
		if (V1_16Release.isPowderSnow(block))
			return BlockKind.POWDER_SNOW;
		if (block == Blocks.WATER)
			return BlockKind.WATER;
		if (state.getMaterial() == Material.ICE)
			return BlockKind.ICE;
		if (block == Blocks.MOVING_PISTON)
			return BlockKind.MOVING_PISTON;
		return BlockKind.OTHER;
	}

	@Override
	public Set<String> tags(Block block) {
		return V1_16Release.tags(block);
	}

	@Override
	public Set<MovementBehaviour> behaviours(Block block) {
		Set<MovementBehaviour> behaviours = EnumSet.noneOf(MovementBehaviour.class);
		if (block instanceof SlimeBlock)
			behaviours.add(MovementBehaviour.SLIME_STEP);
		if (block instanceof HoneyBlock)
			behaviours.add(MovementBehaviour.HONEY_SLIDE);
		if (block instanceof BubbleColumnBlock)
			behaviours.add(MovementBehaviour.BUBBLE_COLUMN);
		return behaviours;
	}
}
