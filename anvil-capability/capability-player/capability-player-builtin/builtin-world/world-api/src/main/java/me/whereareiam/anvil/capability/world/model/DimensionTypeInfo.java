package me.whereareiam.anvil.capability.world.model;

import lombok.Value;
import me.whereareiam.anvil.capability.world.type.Environment;
import org.jetbrains.annotations.NotNull;

/**
 * The type of the dimension a level is, as the client knows it.
 */
@Value
public class DimensionTypeInfo {
	/**
	 * The type's key, with its namespace, such as {@code minecraft:the_nether} or a data pack's {@code example:void}.
	 */
	@NotNull String key;
	/**
	 * How the client renders a level of the type, by the type's sky and shading of block faces.
	 */
	@NotNull Environment environment;
}
