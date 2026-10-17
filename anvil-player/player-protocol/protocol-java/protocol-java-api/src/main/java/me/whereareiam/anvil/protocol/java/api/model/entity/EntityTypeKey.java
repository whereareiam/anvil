package me.whereareiam.anvil.protocol.java.api.model.entity;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The namespaced key of an entity type in the game's registry, such as {@code minecraft:armor_stand}.
 */
@Value
public class EntityTypeKey {
	/**
	 * Namespace of the game's own entity types.
	 */
	public static final String MINECRAFT = "minecraft";

	/**
	 * The key's namespace, such as {@code minecraft}.
	 */
	@NotNull String namespace;
	/**
	 * The key's path within its namespace, such as {@code armor_stand}.
	 */
	@NotNull String path;

	/**
	 * Returns the key of one of the game's own entity types.
	 *
	 * @param path the type's path, such as {@code text_display}
	 * @return the key in the {@code minecraft} namespace
	 */
	public static @NotNull EntityTypeKey minecraft(@NotNull String path) {
		return new EntityTypeKey(MINECRAFT, path);
	}

	/**
	 * Returns the key as the game writes it, its namespace and path separated by a colon.
	 *
	 * @return the key, such as {@code minecraft:armor_stand}
	 */
	@Override
	public @NotNull String toString() {
		return namespace + ":" + path;
	}
}
