package me.whereareiam.anvil.capability.world.model.block;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import me.whereareiam.anvil.capability.world.exception.UnknownBlockNameException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * One block of the player's level as its client knows it: where it is, the global ID of its state in the player's
 * protocol, and what that state is by name. Anvil names the vanilla states of every protocol it supports; a state it
 * cannot name, such as a modded server's block, leaves the block unnamed, and the methods that read the name throw.
 *
 * <pre>{@code
 * Block door = world.blocks().block(new BlockPosition(12, 64, -3)).orElseThrow();
 * assertEquals("minecraft:oak_door", door.name());
 * assertEquals(Optional.of("true"), door.property("open"));
 * }</pre>
 */
@Value
public class Block {
	/**
	 * Where the block is.
	 */
	@NotNull BlockPosition position;
	/**
	 * The global ID of the block's state in the player's protocol.
	 */
	int state;
	@Getter(AccessLevel.NONE)
	@Nullable BlockIdentity identity;
	/**
	 * The protocol the state ID belongs to.
	 */
	int protocol;

	/**
	 * Returns what the block's state is by name.
	 *
	 * @return the state's name and properties, or empty when Anvil cannot name the state
	 */
	public @NotNull Optional<BlockIdentity> identity() {
		return Optional.ofNullable(identity);
	}

	/**
	 * Returns what the block's state is by name, which the block must have.
	 *
	 * @return the state's name and properties
	 * @throws UnknownBlockNameException when Anvil cannot name the state
	 */
	public @NotNull BlockIdentity requireIdentity() {
		if (identity == null) throw new UnknownBlockNameException(this);

		return identity;
	}

	/**
	 * Returns the block's name with its namespace.
	 *
	 * @return the name, such as {@code minecraft:stone}
	 * @throws UnknownBlockNameException when Anvil cannot name the state
	 */
	public @NotNull String name() {
		return requireIdentity().getName();
	}

	/**
	 * Returns the value of one property of the block's state.
	 *
	 * @param name the property's name
	 * @return its value, or empty when the block has no such property
	 * @throws UnknownBlockNameException when Anvil cannot name the state
	 */
	public @NotNull Optional<String> property(@NotNull String name) {
		return requireIdentity().property(name);
	}

	/**
	 * Returns the values of every property of the block's state.
	 *
	 * @return the properties by name
	 * @throws UnknownBlockNameException when Anvil cannot name the state
	 */
	public @NotNull Map<String, String> properties() {
		return requireIdentity().getProperties();
	}

	/**
	 * Tells whether the block is air, which the game treats as no block.
	 *
	 * @return whether the state is one of air's
	 * @throws UnknownBlockNameException when Anvil cannot name the state
	 */
	public boolean isAir() {
		return requireIdentity().isAir();
	}
}
