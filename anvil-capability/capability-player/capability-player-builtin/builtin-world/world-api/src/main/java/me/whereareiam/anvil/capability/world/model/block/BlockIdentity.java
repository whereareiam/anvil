package me.whereareiam.anvil.capability.world.model.block;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;

/**
 * What a block state is by name: the block, with its namespace, and the values of the block's properties in that
 * state, as the game names them in commands, such as {@code minecraft:oak_door} with {@code open=true}.
 */
@Value
public class BlockIdentity {
	/**
	 * The block's name with its namespace, such as {@code minecraft:stone}.
	 */
	@NotNull String name;
	/**
	 * Each property of the block, by name, with its value in this state; empty for a block without properties.
	 */
	@NotNull Map<String, String> properties;
	/**
	 * Whether the state is one of air's, which the game treats as no block.
	 */
	boolean air;

	/**
	 * Returns the value of one property in this state.
	 *
	 * @param name the property's name, such as {@code facing}
	 * @return its value, or empty when the block has no such property
	 */
	public @NotNull Optional<String> property(@NotNull String name) {
		return Optional.ofNullable(properties.get(name));
	}
}
