package me.whereareiam.anvil.edition.java.physics.entity;

import me.whereareiam.anvil.edition.java.data.block.model.BlockState;
import me.whereareiam.anvil.edition.java.data.block.model.movement.BlockMovement;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.MovementBehaviour;
import me.whereareiam.anvil.edition.java.physics.EntityBehaviour;
import me.whereareiam.anvil.edition.java.physics.geometry.Box;
import me.whereareiam.anvil.edition.java.physics.geometry.Coordinates;
import me.whereareiam.anvil.edition.java.physics.geometry.Vector;
import me.whereareiam.anvil.edition.java.physics.world.BlockReader;
import me.whereareiam.anvil.edition.java.physics.world.Collisions;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * What the blocks around an entity do to its movement, as vanilla's blocks and {@code Entity} apply it: the block it
 * stands on ({@code getOnPos}, {@code checkSupportingBlock}) and the one below that gives its friction and its speed
 * and jump factors, landing ({@code updateEntityMovementAfterFallOn}), walking on slime ({@code stepOn}), and the
 * blocks it is inside: cobwebs, berry bushes and powder snow slow it, honey slides it down its side and bubble columns
 * push it ({@code entityInside}). Before 1.21.2 the blocks inside the box apply during the movement; from 1.21.2 after
 * it, over the box swept from the tick's start to its end ({@code applyEffectsFromBlocks}).
 */
public final class BlockEffects {
	private static final Set<BlockKind> TALL = Set.of(BlockKind.FENCE, BlockKind.WALL, BlockKind.FENCE_GATE);

	private final @NotNull EntityBody body;
	private final @NotNull BlockReader reader;
	private final @NotNull Collisions collisions;
	private final @NotNull InsideBlocks inside;

	/**
	 * Creates the block effects of an entity.
	 *
	 * @param body the entity
	 */
	public BlockEffects(@NotNull EntityBody body) {
		this.body = body;
		this.reader = body.level();
		this.collisions = new Collisions(reader);
		this.inside = new InsideBlocks(body);
	}

	/**
	 * Returns the block an offset below the entity's feet, which is the block it stands on where it stands on one
	 * ({@code Entity.getOnPos}).
	 *
	 * @param offset how far below the feet
	 * @return the block's position
	 */
	public int @NotNull [] onPos(float offset) {
		Vector position = body.position();
		int[] below = {Coordinates.floor(position.x), Coordinates.floor(position.y - offset), Coordinates.floor(position.z)};
		if (!body.has(EntityBehaviour.SUPPORTING_BLOCK)) return legacyOnPos(below);

		int[] supporting = body.supportingBlock();
		if (supporting == null) return below;
		if (!(offset > 1.0E-5F)) return supporting;

		BlockKind kind = state(supporting).block().kind();
		boolean keep = offset <= 0.5 && kind == BlockKind.FENCE || kind == BlockKind.WALL || kind == BlockKind.FENCE_GATE;
		return keep ? supporting : new int[] {supporting[0], below[1], supporting[2]};
	}

	private int @NotNull [] legacyOnPos(int @NotNull [] below) {
		if (!state(below).isAir()) return below;

		int[] lower = {below[0], below[1] - 1, below[2]};
		return TALL.contains(state(lower).block().kind()) ? lower : below;
	}

	/**
	 * The friction of the block below the entity ({@code getBlockPosBelowThatAffectsMyMovement}).
	 *
	 * @return the friction
	 */
	public float friction() {
		return movement(below()).friction();
	}

	/**
	 * Tells whether the client has the chunk column of the block below the entity.
	 *
	 * @return whether it is loaded
	 */
	public boolean hasChunkBelow() {
		int[] below = below();
		return reader.hasChunkAt(below[0], below[2]);
	}

	/**
	 * The jump factor of the block the entity is in, else of the block below it ({@code getBlockJumpFactor}).
	 *
	 * @return the factor
	 */
	public float jumpFactor() {
		float inside = movement(body.blockPosition()).jumpFactor();
		return inside == 1.0 ? movement(below()).jumpFactor() : inside;
	}

	/**
	 * The speed factor of the block the entity is in, else of the block below it, unless it is in water or a bubble
	 * column ({@code Entity.getBlockSpeedFactor}), as the entity takes it ({@link EntityBody#blockSpeedFactor}).
	 *
	 * @return the factor
	 */
	public float speedFactor() {
		BlockState inside = state(body.blockPosition());
		float factor = inside.block().movement().speedFactor();
		boolean water = inside.block().kind() == BlockKind.WATER
				|| inside.block().movement().has(MovementBehaviour.BUBBLE_COLUMN);
		float block = water || factor != 1.0 ? factor : movement(below()).speedFactor();
		return body.blockSpeedFactor(block);
	}

	/**
	 * Stops or bounces the entity's fall on a block it landed on ({@code updateEntityMovementAfterFallOn}): slime blocks
	 * and beds bounce an entity that does not suppress the bounce ({@link EntityBody#suppressingBounce}), other blocks
	 * stop its vertical movement.
	 *
	 * @param landed the block's position
	 */
	public void fallOn(int @NotNull [] landed) {
		double bounce = movement(landed).bounceFactor();
		Vector delta = body.deltaMovement();
		if (bounce == 0.0 || body.suppressingBounce()) {
			body.deltaMovement(delta.multiply(1.0, 0.0, 1.0));
			return;
		}
		if (delta.y < 0.0) body.deltaMovement(new Vector(delta.x, -delta.y * bounce * 1.0, delta.z));
	}

	/**
	 * Slows an entity walking on slime that does not step carefully, which its shift key makes it
	 * ({@code SlimeBlock.stepOn}, {@code Entity.isSteppingCarefully}).
	 *
	 * @param block the block's position
	 */
	public void stepOn(int @NotNull [] block) {
		if (!movement(block).has(MovementBehaviour.SLIME_STEP)) return;

		double vertical = Math.abs(body.deltaMovement().y);
		if (!(vertical < 0.1) || body.shiftKeyDown()) return;

		double factor = 0.4 + vertical * 0.2;
		body.deltaMovement(body.deltaMovement().multiply(factor, 1.0, factor));
	}

	/**
	 * Remembers the block the entity landed on ({@code Entity.checkSupportingBlock}), from 1.20.
	 *
	 * @param movement the movement that collided
	 */
	public void checkSupportingBlock(@NotNull Vector movement) {
		if (!body.onGround()) {
			body.onGroundNoBlocks(false);
			body.supportingBlock(null);
			return;
		}

		Box box = body.box();
		Box below = new Box(new Vector(box.minX, box.minY - 1.0E-6, box.minZ), new Vector(box.maxX, box.minY, box.maxZ));
		int[] found = collisions.supportingBlock(below, body.position(), body.context());
		if (found == null && !body.onGroundNoBlocks())
			found = collisions.supportingBlock(below.move(-movement.x, 0.0, -movement.z), body.position(), body.context());
		body.supportingBlock(found);
		body.onGroundNoBlocks(found == null);
	}

	/**
	 * Applies the blocks inside the entity's box during its movement, as the clients before 1.21.2 do
	 * ({@code checkInsideBlocks}), with the box inset by {@code 0.001}, from 1.20 by {@code 1.0E-7}.
	 */
	public void insideDuringMove() {
		inside.apply(body.box().deflate(body.has(EntityBehaviour.SUPPORTING_BLOCK) ? 1.0E-7 : 0.001));
	}

	/**
	 * Applies the block stood on and the blocks passed through this tick, as the clients from 1.21.2 do after the
	 * movement ({@code applyEffectsFromBlocks}): the boxes at the tick's start and end, inset by {@code 1.0E-5F}, and
	 * everything between them.
	 */
	public void afterMove() {
		if (body.onGround()) stepOn(onPos(0.2F));

		EntityDimensions dimensions = body.dimensions();
		Box from = Box.at(body.oldPosition(), dimensions.width(), dimensions.height()).deflate(1.0E-5F);
		inside.apply(from.union(body.box().deflate(1.0E-5F)));
	}

	private int @NotNull [] below() {
		if (body.has(EntityBehaviour.SUPPORTING_BLOCK)) return onPos(0.500001F);

		Vector position = body.position();
		return new int[] {Coordinates.floor(position.x), Coordinates.floor(body.box().minY - 0.5000001),
				Coordinates.floor(position.z)};
	}

	private @NotNull BlockState state(int @NotNull [] block) {
		return reader.state(block[0], block[1], block[2]);
	}

	private @NotNull BlockMovement movement(int @NotNull [] block) {
		return state(block).block().movement();
	}
}
