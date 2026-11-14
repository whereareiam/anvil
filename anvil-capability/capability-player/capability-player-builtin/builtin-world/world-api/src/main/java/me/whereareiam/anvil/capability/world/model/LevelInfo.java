package me.whereareiam.anvil.capability.world.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.capability.world.type.Environment;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * What the client was told about the level it is in when it joined or respawned in it.
 */
@Value
@Builder(toBuilder = true)
public class LevelInfo {
	/**
	 * The key of the level, with its namespace, such as {@code minecraft:the_nether}: the dimension the player is in.
	 */
	@NotNull String dimension;
	/**
	 * The key of the level's dimension type, with its namespace, such as {@code minecraft:the_nether}, or a data
	 * pack's {@code example:void}.
	 */
	@NotNull String dimensionType;
	/**
	 * How the client renders the level, by its dimension type's sky and shading.
	 */
	@NotNull Environment environment;
	/**
	 * Whether the server is in hardcore mode, where a player who dies cannot respawn.
	 */
	boolean hardcore;
	/**
	 * Whether the level is a debug world.
	 */
	boolean debug;
	/**
	 * Whether the level is a superflat world.
	 */
	boolean flat;
	/**
	 * The keys of every level the server named when the player joined it, in the server's order.
	 */
	@NotNull @Singular List<String> levels;
}
