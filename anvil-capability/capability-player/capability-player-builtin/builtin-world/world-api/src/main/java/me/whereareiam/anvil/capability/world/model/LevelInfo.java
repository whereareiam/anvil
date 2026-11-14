package me.whereareiam.anvil.capability.world.model;

import lombok.Value;
import me.whereareiam.anvil.capability.world.type.LevelGeneration;
import org.jetbrains.annotations.NotNull;

/**
 * What the client was told about the level it is in when it joined or respawned in it.
 */
@Value
public class LevelInfo {
	/**
	 * The key of the level, with its namespace, such as {@code minecraft:the_nether}: the dimension the player is in.
	 */
	@NotNull String dimension;
	/**
	 * The level's dimension type.
	 */
	@NotNull DimensionTypeInfo type;
	/**
	 * How the server generates the level.
	 */
	@NotNull LevelGeneration generation;
}
