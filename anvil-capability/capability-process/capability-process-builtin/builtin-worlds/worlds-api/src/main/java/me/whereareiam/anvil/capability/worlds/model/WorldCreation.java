package me.whereareiam.anvil.capability.worlds.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.capability.worlds.type.WorldGenerator;
import me.whereareiam.anvil.capability.worlds.type.WorldKind;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A world for a server to create while it runs, as a plugin creates one.
 *
 * <pre>{@code
 * worlds.create(WorldCreation.builder()
 * 		.key("arena_nether")
 * 		.kind(WorldKind.NETHER)
 * 		.seed(42L)
 * 		.build());
 * }</pre>
 */
@Value
@Builder
public class WorldCreation {
	/**
	 * The key of the new world's level, such as {@code minecraft:arena}; a key without a namespace is in the
	 * {@code minecraft} namespace. Paper and Spigot create a world under the name of the key's path, so they create
	 * keys in the {@code minecraft} namespace whose path is a plain name, without {@code /}.
	 */
	@NotNull String key;
	/**
	 * The kind of dimension the world is: {@link WorldKind#OVERWORLD}, {@link WorldKind#NETHER} or
	 * {@link WorldKind#END}. A server creates no {@link WorldKind#CUSTOM} world.
	 */
	@NotNull @Builder.Default WorldKind kind = WorldKind.OVERWORLD;
	/**
	 * The seed the world generates its terrain from, or {@code null} for a seed the server picks.
	 */
	@Nullable Long seed;
	/**
	 * How the world generates its terrain.
	 */
	@NotNull @Builder.Default WorldGenerator generator = WorldGenerator.DEFAULT;
}
