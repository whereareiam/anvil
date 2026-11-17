package me.whereareiam.anvil.capability.movement.java.physics.simulation;

import me.whereareiam.anvil.capability.movement.java.physics.geometry.Coordinates;
import me.whereareiam.anvil.capability.movement.java.physics.ClientBehaviour;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Box;
import me.whereareiam.anvil.capability.movement.java.physics.geometry.Vector;
import me.whereareiam.anvil.capability.movement.java.physics.server.PlayerAttribute;
import me.whereareiam.anvil.capability.movement.java.physics.world.BlockReader;
import me.whereareiam.anvil.capability.movement.java.physics.world.Collisions;
import me.whereareiam.anvil.protocol.java.block.model.BlockState;
import me.whereareiam.anvil.protocol.java.block.model.movement.BlockMovement;
import me.whereareiam.anvil.protocol.java.block.type.BlockKind;
import me.whereareiam.anvil.protocol.java.block.type.MovementBehaviour;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * What the blocks around the player do to its movement, as vanilla's blocks and {@code Entity} apply it: the block it
 * stands on ({@code getOnPos}, {@code checkSupportingBlock}) and the one below that gives its friction and its speed
 * and jump factors, landing ({@code updateEntityMovementAfterFallOn}), walking on slime ({@code stepOn}), and the
 * blocks it is inside: cobwebs, berry bushes and powder snow slow it, honey slides it down its side and bubble columns
 * push it ({@code entityInside}). Before 1.21.2 the blocks inside the box apply during the movement; from 1.21.2 after
 * it, over the box swept from the tick's start to its end ({@code applyEffectsFromBlocks}).
 */
public final class BlockEffects {
	private static final Set<BlockKind> TALL = Set.of(BlockKind.FENCE, BlockKind.WALL, BlockKind.FENCE_GATE);

	private final @NotNull PlayerBody body;
	private final @NotNull BlockReader reader;
	private final @NotNull Collisions collisions;
	private final @NotNull InsideBlocks inside;

	/**
	 * Creates the block effects of a player.
	 *
	 * @param body the player
	 * @param reader the blocks its client knows
	 */
	public BlockEffects(@NotNull PlayerBody body, @NotNull BlockReader reader) {
		this.body = body;
		this.reader = reader;
		this.collisions = new Collisions(reader);
		this.inside = new InsideBlocks(body, reader);
	}

	/**
	 * Returns the block an offset below the player's feet, which is the block it stands on where it stands on one
	 * ({@code Entity.getOnPos}).
	 *
	 * @param offset how far below the feet
	 * @return the block's position
	 */
	public int @NotNull [] onPos(float offset) {
		Vector position = body.getPosition();
		int[] below = {Coordinates.floor(position.x), Coordinates.floor(position.y - offset), Coordinates.floor(position.z)};
		if (!body.has(ClientBehaviour.SUPPORTING_BLOCK)) return legacyOnPos(below);

		int[] supporting = body.getSupportingBlock();
		if (supporting == null) return below;
		if (!(offset > 1.0E-5F)) return supporting;

		BlockKind kind = state(supporting).getBlock().getKind();
		boolean keep = offset <= 0.5 && kind == BlockKind.FENCE || kind == BlockKind.WALL || kind == BlockKind.FENCE_GATE;
		return keep ? supporting : new int[] {supporting[0], below[1], supporting[2]};
	}

	private int @NotNull [] legacyOnPos(int @NotNull [] below) {
		if (!state(below).isAir()) return below;

		int[] lower = {below[0], below[1] - 1, below[2]};
		return TALL.contains(state(lower).getBlock().getKind()) ? lower : below;
	}

	/**
	 * The friction of the block below the player ({@code getBlockPosBelowThatAffectsMyMovement}).
	 *
	 * @return the friction
	 */
	public float friction() {
		return movement(below()).getFriction();
	}

	/**
	 * Tells whether the client has the chunk column of the block below the player.
	 *
	 * @return whether it is loaded
	 */
	public boolean hasChunkBelow() {
		int[] below = below();
		return reader.hasChunkAt(below[0], below[2]);
	}

	/**
	 * The jump factor of the block the player is in, else of the block below it ({@code getBlockJumpFactor}).
	 *
	 * @return the factor
	 */
	public float jumpFactor() {
		float inside = movement(body.blockPosition()).getJumpFactor();
		return inside == 1.0 ? movement(below()).getJumpFactor() : inside;
	}

	/**
	 * The speed factor of the block the player is in, else of the block below it, unless it is in water or a bubble
	 * column ({@code Entity.getBlockSpeedFactor}); from 1.21 eased towards one by the movement efficiency attribute
	 * ({@code LivingEntity.getBlockSpeedFactor}).
	 *
	 * @return the factor
	 */
	public float speedFactor() {
		BlockState inside = state(body.blockPosition());
		float factor = inside.getBlock().getMovement().getSpeedFactor();
		boolean water = inside.getBlock().getKind() == BlockKind.WATER
				|| inside.getBlock().getMovement().has(MovementBehaviour.BUBBLE_COLUMN);
		float block = water || factor != 1.0 ? factor : movement(below()).getSpeedFactor();
		float efficiency = (float) body.getServer().getAttributes().value(PlayerAttribute.MOVEMENT_EFFICIENCY);
		return block + efficiency * (1.0F - block);
	}

	/**
	 * Stops or bounces the player's fall on a block it landed on ({@code updateEntityMovementAfterFallOn}): slime blocks
	 * and beds bounce a player that does not sneak, other blocks stop its vertical movement.
	 *
	 * @param landed the block's position
	 */
	public void fallOn(int @NotNull [] landed) {
		double bounce = movement(landed).getBounceFactor();
		Vector delta = body.getDeltaMovement();
		if (bounce == 0.0 || body.getKeys().isSneak()) {
			body.setDeltaMovement(delta.multiply(1.0, 0.0, 1.0));
			return;
		}
		if (delta.y < 0.0) body.setDeltaMovement(new Vector(delta.x, -delta.y * bounce * 1.0, delta.z));
	}

	/**
	 * Slows a player walking on slime without sneaking ({@code SlimeBlock.stepOn}).
	 *
	 * @param block the block's position
	 */
	public void stepOn(int @NotNull [] block) {
		if (!movement(block).has(MovementBehaviour.SLIME_STEP)) return;

		double vertical = Math.abs(body.getDeltaMovement().y);
		if (!(vertical < 0.1) || body.getKeys().isSneak()) return;

		double factor = 0.4 + vertical * 0.2;
		body.setDeltaMovement(body.getDeltaMovement().multiply(factor, 1.0, factor));
	}

	/**
	 * Remembers the block the player landed on ({@code Entity.checkSupportingBlock}), from 1.20.
	 *
	 * @param movement the movement that collided
	 */
	public void checkSupportingBlock(@NotNull Vector movement) {
		if (!body.isOnGround()) {
			body.setOnGroundNoBlocks(false);
			body.setSupportingBlock(null);
			return;
		}

		Box box = body.getBox();
		Box below = new Box(new Vector(box.minX, box.minY - 1.0E-6, box.minZ), new Vector(box.maxX, box.minY, box.maxZ));
		int[] found = collisions.supportingBlock(below, body.getPosition(), body.context());
		if (found == null && !body.isOnGroundNoBlocks())
			found = collisions.supportingBlock(below.move(-movement.x, 0.0, -movement.z), body.getPosition(), body.context());
		body.setSupportingBlock(found);
		body.setOnGroundNoBlocks(found == null);
	}

	/**
	 * Applies the blocks inside the player's box during its movement, as the clients before 1.21.2 do
	 * ({@code checkInsideBlocks}), with the box inset by {@code 0.001}, from 1.20 by {@code 1.0E-7}.
	 */
	public void insideDuringMove() {
		inside.apply(body.getBox().deflate(body.has(ClientBehaviour.SUPPORTING_BLOCK) ? 1.0E-7 : 0.001));
	}

	/**
	 * Applies the block stood on and the blocks passed through this tick, as the clients from 1.21.2 do after the
	 * movement ({@code applyEffectsFromBlocks}): the boxes at the tick's start and end, inset by {@code 1.0E-5F}, and
	 * everything between them.
	 */
	public void afterMove() {
		if (body.isOnGround()) stepOn(onPos(0.2F));

		Box from = Box.at(body.getOldPosition(), body.getWidth(), body.getHeight()).deflate(1.0E-5F);
		inside.apply(from.union(body.getBox().deflate(1.0E-5F)));
	}

	private int @NotNull [] below() {
		if (body.has(ClientBehaviour.SUPPORTING_BLOCK)) return onPos(0.500001F);

		Vector position = body.getPosition();
		return new int[] {Coordinates.floor(position.x), Coordinates.floor(body.getBox().minY - 0.5000001),
				Coordinates.floor(position.z)};
	}

	private @NotNull BlockState state(int @NotNull [] block) {
		return reader.state(block[0], block[1], block[2]);
	}

	private @NotNull BlockMovement movement(int @NotNull [] block) {
		return state(block).getBlock().getMovement();
	}
}
