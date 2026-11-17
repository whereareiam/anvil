package me.whereareiam.anvil.protocol.java.block.model.collision;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.block.type.CollisionKind;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The collision shape of a block state: the boxes an entity cannot move into, in block-local coordinates.
 *
 * <p>The boxes are vanilla's, as {@code VoxelShape.toAabbs} gives them. Where vanilla's shape also depends on the
 * player or on the block's position, the block's {@link CollisionKind} is {@link CollisionKind#DYNAMIC} or
 * {@link CollisionKind#OFFSET}: a state's shape for each player case is
 * {@link me.whereareiam.anvil.protocol.java.block.model.BlockState#collision(CollisionCase)}, and an offset block's
 * shape is the one before vanilla moves it.</p>
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
