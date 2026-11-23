package me.whereareiam.anvil.edition.java.physics.entity;

import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.movement.BlockMovement;
import me.whereareiam.anvil.edition.java.data.block.model.movement.StuckSpeed;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.MovementBehaviour;
import me.whereareiam.anvil.edition.java.physics.geometry.Box;
import me.whereareiam.anvil.edition.java.physics.geometry.Coordinates;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.world.BlockReader;
import org.jetbrains.annotations.NotNull;

/**
 * The blocks the entity is inside, as their {@code entityInside} moves a living entity: cobwebs, berry bushes and
 * powder snow, the last only while the entity's feet are in powder snow too, slow it ({@code makeStuckInBlock}), honey
 * slides it down its side ({@code HoneyBlock.doSlideMovement}) and bubble columns push it up or drag it down.
 */
final class InsideBlocks {
	private final @NotNull EntityBody body;
	private final @NotNull BlockReader reader;

	InsideBlocks(@NotNull EntityBody body) {
		this.body = body;
		this.reader = body.level();
	}

	/**
	 * Applies {@code entityInside} of every block a box touches, east-west outermost, then up, then north-south, where
	 * the client has every column it touches.
	 */
	void apply(@NotNull Box box) {
		int[] min = {Coordinates.floor(box.minX), Coordinates.floor(box.minY), Coordinates.floor(box.minZ)};
		int[] max = {Coordinates.floor(box.maxX), Coordinates.floor(box.maxY), Coordinates.floor(box.maxZ)};
		if (!reader.hasChunksAt(min[0], min[2], max[0], max[2])) return;

		for (int x = min[0]; x <= max[0]; x++)
			for (int y = min[1]; y <= max[1]; y++)
				for (int z = min[2]; z <= max[2]; z++) entityInside(new int[] {x, y, z});
	}

	/**
	 * {@code entityInside} of the blocks that move an entity inside them.
	 */
	private void entityInside(int @NotNull [] block) {
		BlockState state = state(block);
		BlockMovement movement = state.block().movement();
		StuckSpeed stuck = movement.stuckSpeed();
		boolean powderSnow = state.block().kind() == BlockKind.POWDER_SNOW;
		if (stuck != null && (!powderSnow || state(body.blockPosition()).block() == state.block())) makeStuck(stuck);
		if (movement.has(MovementBehaviour.HONEY_SLIDE) && slidingDown(block)) slideDown();
		if (movement.has(MovementBehaviour.BUBBLE_COLUMN)) bubbleColumn(state, block);
	}

	private void makeStuck(@NotNull StuckSpeed stuck) {
		body.resetFallDistance();
		body.stuckSpeedMultiplier(new Vector(stuck.horizontal(), stuck.vertical(), stuck.horizontal()));
	}

	/**
	 * {@code HoneyBlock.isSlidingDown}: in the air against the block's side, falling faster than {@code 0.08}.
	 */
	private boolean slidingDown(int @NotNull [] block) {
		Vector position = body.position();
		if (body.onGround() || position.y > block[1] + 0.9375 - 1.0E-7 || body.deltaMovement().y >= -0.08)
			return false;

		double reach = 0.4375 + body.dimensions().width() / 2.0F;
		return Math.abs(block[0] + 0.5 - position.x) + 1.0E-7 > reach
				|| Math.abs(block[2] + 0.5 - position.z) + 1.0E-7 > reach;
	}

	private void slideDown() {
		Vector delta = body.deltaMovement();
		body.resetFallDistance();
		if (!(delta.y < -0.13)) {
			body.deltaMovement(new Vector(delta.x, -0.05, delta.z));
			return;
		}

		double factor = -0.05 / delta.y;
		body.deltaMovement(new Vector(delta.x * factor, -0.05, delta.z * factor));
	}

	/**
	 * {@code BubbleColumnBlock.entityInside} as 1.21.1 decides it: below air the column pushes the entity up to
	 * {@code 1.8} or drags it down to {@code -0.9} ({@code onAboveBubbleCol}); inside it, to {@code 0.7} or {@code -0.3}
	 * ({@code onInsideBubbleColumn}), which resets the fall distance.
	 */
	private void bubbleColumn(@NotNull BlockState state, int @NotNull [] block) {
		boolean drag = "true".equals(state.property("drag").orElse("false"));
		Vector delta = body.deltaMovement();
		if (state(new int[] {block[0], block[1] + 1, block[2]}).isAir()) {
			body.deltaMovement(new Vector(delta.x, drag ? Math.max(-0.9, delta.y - 0.03) : Math.min(1.8, delta.y + 0.1),
					delta.z));
			return;
		}

		body.deltaMovement(new Vector(delta.x, drag ? Math.max(-0.3, delta.y - 0.03) : Math.min(0.7, delta.y + 0.06),
				delta.z));
		body.resetFallDistance();
	}

	private @NotNull BlockState state(int @NotNull [] block) {
		return reader.state(block[0], block[1], block[2]);
	}
}
