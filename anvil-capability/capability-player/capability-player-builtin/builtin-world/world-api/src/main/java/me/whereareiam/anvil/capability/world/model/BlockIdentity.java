package me.whereareiam.anvil.capability.world.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What a block state is in the player's release: the block's name and the values of its properties, from the block
 * data Anvil ships for that release.
 */
@Value
public class BlockIdentity {
	/**
	 * The block's namespaced name, such as {@code minecraft:oak_stairs}.
	 */
	@NotNull String name;
	/**
	 * The value of each of the block's properties, in the block's property order; empty for a block without any.
	 */
	@NotNull Map<String, String> properties;
	/**
	 * Whether the block is air: {@code air}, {@code cave_air} or {@code void_air}.
	 */
	boolean air;

	/**
	 * Returns the value of one property.
	 *
	 * @param name the property's name, such as {@code half}
	 * @return the value, or nothing when the block has no such property
	 */
	public @NotNull Optional<String> property(@NotNull String name) {
		return Optional.ofNullable(properties.get(name));
	}

	/**
	 * Writes the state as vanilla writes a block state string, such as
	 * {@code minecraft:oak_door[half=lower,open=true]}.
	 *
	 * @return the block state string
	 */
	public @NotNull String describe() {
		if (properties.isEmpty()) return name;

		return properties.entrySet().stream()
				.map(property -> property.getKey() + "=" + property.getValue())
				.collect(Collectors.joining(",", name + "[", "]"));
	}
}
