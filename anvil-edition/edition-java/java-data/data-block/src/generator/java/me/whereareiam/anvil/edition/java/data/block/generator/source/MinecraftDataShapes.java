package me.whereareiam.anvil.edition.java.data.block.generator.source;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * minecraft-data's {@code blockCollisionShapes.json}: a table of shapes, each a list of boxes, and the shape of each
 * block's states.
 */
@Value
@Builder
@Jacksonized
public class MinecraftDataShapes {
	/**
	 * The shape of each block's states, by the block's name without namespace.
	 */
	@Singular
	@NotNull Map<String, ShapeIndices> blocks;
	/**
	 * The shapes by their index, each a list of boxes of six block-local coordinates
	 * {@code minX, minY, minZ, maxX, maxY, maxZ}.
	 */
	@Singular
	@NotNull Map<Integer, List<double[]>> shapes;
}
