package me.whereareiam.anvil.capability.worlds.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.capability.worlds.type.WorldKind;
import org.jetbrains.annotations.NotNull;

/**
 * A world a server has loaded, as the server reports it.
 */
@Value
@Builder
public class ServerWorld {
	/**
	 * The key of the world's level, with its namespace, such as {@code minecraft:overworld}: the dimension its players'
	 * clients are told they are in. A server with several worlds of one kind gives each a key of its own, such as
	 * {@code minecraft:arena} for a world a plugin created under the name {@code arena}.
	 */
	@NotNull String key;
	/**
	 * The kind of dimension the world is.
	 */
	@NotNull WorldKind kind;
	/**
	 * Where players arrive in the world, such as those it is the respawn world of, or those a test sends to it without
	 * a location. Bukkit keeps a spawn for each world; a NeoForge server keeps one for its respawn world, which it
	 * reports for every world, at the same position in each.
	 */
	@NotNull WorldLocation spawn;
}
