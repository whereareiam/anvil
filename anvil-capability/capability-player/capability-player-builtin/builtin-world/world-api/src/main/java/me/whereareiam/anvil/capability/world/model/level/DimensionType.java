package me.whereareiam.anvil.capability.world.model.level;

import lombok.Value;
import me.whereareiam.anvil.capability.world.type.Environment;
import org.jetbrains.annotations.NotNull;

/**
 * The type of the dimension a level is, as the client knows it: how the client renders its levels and which block
 * heights they span, from {@link #minY()} up to just below {@code minY + height}.
 */
@Value
public class DimensionType {
	/**
	 * The type's key, with its namespace, such as {@code minecraft:the_nether} or a data pack's {@code example:void}.
	 */
	@NotNull String key;
	/**
	 * How the client renders a level of the type, by the type's sky and shading of block faces.
	 */
	@NotNull Environment environment;
	/**
	 * The lowest block height of a level of the type, such as {@code -64} for the overworld from Minecraft 1.18 on.
	 */
	int minY;
	/**
	 * The number of block heights a level of the type spans from {@link #minY()} up, such as {@code 384} for the
	 * overworld from Minecraft 1.18 on; every type spans 256 before Minecraft 1.17.
	 */
	int height;
}
