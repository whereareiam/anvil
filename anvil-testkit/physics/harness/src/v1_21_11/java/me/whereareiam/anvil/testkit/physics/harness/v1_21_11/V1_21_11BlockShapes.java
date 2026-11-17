package me.whereareiam.anvil.testkit.physics.harness.v1_21_11;

import me.whereareiam.anvil.testkit.physics.harness.block.BlockFace;
import me.whereareiam.anvil.testkit.physics.harness.block.CollisionCase;
import me.whereareiam.anvil.testkit.physics.harness.block.ShapeBox;
import me.whereareiam.anvil.testkit.physics.harness.block.ShapeProbe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the collision shapes, offsets and sturdy faces of the blocks of the vanilla client of Minecraft 1.21.11 for a
 * player in each collision case.
 */
final class V1_21_11BlockShapes implements ShapeProbe<Block, BlockState> {
	private final ProbePlayer player;

	V1_21_11BlockShapes(ProbePlayer player) {
		this.player = player;
	}

	@Override
	public List<ShapeBox> collision(BlockState state, int x, int y, int z, CollisionCase context) {
		player.setPos(x + 0.5, y + context.getBottom().height(), z + 0.5);
		player.descending = context.isDescending();
		player.fallDistance = context.isFalling() ? CollisionCase.FALL_DISTANCE : 0.0;
		player.setItemSlot(EquipmentSlot.FEET, context.isLeatherBoots() ? new ItemStack(Items.LEATHER_BOOTS) : ItemStack.EMPTY);
		return boxes(state.getCollisionShape(EmptyBlockGetter.INSTANCE, new BlockPos(x, y, z), CollisionContext.of(player)));
	}

	private static List<ShapeBox> boxes(VoxelShape shape) {
		List<ShapeBox> boxes = new ArrayList<>();
		for (AABB box : shape.toAabbs())
			boxes.add(new ShapeBox(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ));
		return boxes;
	}

	@Override
	public boolean offset(Block block) {
		return block.defaultBlockState().hasOffsetFunction();
	}

	@Override
	public double[] offset(BlockState state, int x, int y, int z) {
		Vec3 offset = state.getOffset(new BlockPos(x, y, z));
		return new double[] {offset.x, offset.y, offset.z};
	}

	@Override
	public long offsetSeed(int x, int z) {
		return Mth.getSeed(x, 0, z);
	}

	@Override
	public boolean dynamicShape(Block block) {
		return block.hasDynamicShape();
	}

	@Override
	public Set<BlockFace> sturdyFaces(BlockState state) {
		Set<BlockFace> faces = EnumSet.noneOf(BlockFace.class);
		for (Direction direction : Direction.values())
			if (state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, direction))
				faces.add(BlockFace.valueOf(direction.name()));
		return faces;
	}
}
