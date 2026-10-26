package me.whereareiam.anvil.protocol.java.data.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The collision shape of a block state: the boxes an entity cannot move into, in block-local coordinates.
 *
 * <p>The shape is the one the data records for the state alone. Where vanilla's shape also depends on the entity or
 * on the block's position, the block carries {@code BlockTrait.DYNAMIC_COLLISION} or
 * {@code BlockTrait.OFFSET_COLLISION} and the shape is the single one minecraft-data records, such as scaffolding's
 * shape for an entity standing on it, or bamboo's before its offset.</p>
 */
@Value
public class CollisionShape {
	/**
	 * The shape of a state without collision, such as air, water or a flower.
	 */
	public static final @NotNull CollisionShape EMPTY = new CollisionShape(List.of());

	/**
	 * The boxes of the shape, in the data's order; empty for no collision.
	 */
	@NotNull List<CollisionBox> boxes;

	/**
	 * Tests whether the shape has no box, so entities move through the block.
	 *
	 * @return whether the shape is empty
	 */
	public boolean isEmpty() {
		return boxes.isEmpty();
	}

	/**
	 * Tests whether the shape is exactly one box filling the whole block, as stone's is.
	 *
	 * @return whether the shape is the full block
	 */
	public boolean isFullBlock() {
		return boxes.size() == 1 && boxes.getFirst().isFullBlock();
	}
}
