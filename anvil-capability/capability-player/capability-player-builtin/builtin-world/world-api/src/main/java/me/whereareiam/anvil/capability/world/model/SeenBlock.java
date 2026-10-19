package me.whereareiam.anvil.capability.world.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;
import me.whereareiam.anvil.capability.world.exception.UnknownBlockNameException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/**
 * A block as the player's client knew it at one moment. The value never changes; a read or a wait returns a new one
 * for a later state.
 *
 * <p>Every block has the global ID of its state in the player's release. Its {@link #identity() identity}, the name
 * and properties of that state, is present when Anvil ships block data for the release and absent when it does not;
 * the accessors that need the name throw {@link UnknownBlockNameException} for a block without one rather than
 * guessing.</p>
 *
 * <pre>{@code
 * SeenBlock door = world.block(4, 64, 2).orElseThrow();
 * boolean open = door.isNamed() && door.name().equals("minecraft:oak_door")
 *         && door.property("open").orElseThrow().equals("true");
 * }</pre>
 */
@Value
public class SeenBlock {
	/**
	 * The block's east-west coordinate.
	 */
	int x;
	/**
	 * The block's height.
	 */
	int y;
	/**
	 * The block's north-south coordinate.
	 */
	int z;
	/**
	 * The global ID of the block's state in the player's release, which is {@code 0} for air.
	 */
	int stateId;
	/**
	 * The name and properties of the state, or null when Anvil ships no block data for the player's release.
	 */
	@Getter(AccessLevel.NONE)
	@Nullable BlockIdentity identity;

	/**
	 * Returns the name and properties of the block's state.
	 *
	 * @return the identity, or nothing when Anvil ships no block data for the player's release
	 */
	public @NotNull Optional<BlockIdentity> identity() {
		return Optional.ofNullable(identity);
	}

	/**
	 * Tells whether the block's name and properties are known.
	 *
	 * @return whether Anvil ships block data for the player's release
	 */
	public boolean isNamed() {
		return identity != null;
	}

	/**
	 * Returns the name and properties of the block's state, which the release must know.
	 *
	 * @return the identity
	 * @throws UnknownBlockNameException when Anvil ships no block data for the player's release
	 */
	public @NotNull BlockIdentity requireIdentity() {
		if (identity == null) throw new UnknownBlockNameException(this);

		return identity;
	}

	/**
	 * Returns the block's namespaced name, such as {@code minecraft:stone}.
	 *
	 * @return the name
	 * @throws UnknownBlockNameException when Anvil ships no block data for the player's release
	 */
	public @NotNull String name() {
		return requireIdentity().getName();
	}

	/**
	 * Returns the value of one property of the block's state.
	 *
	 * @param name the property's name, such as {@code facing}
	 * @return the value, or nothing when the block has no such property
	 * @throws UnknownBlockNameException when Anvil ships no block data for the player's release
	 */
	public @NotNull Optional<String> property(@NotNull String name) {
		return requireIdentity().property(name);
	}

	/**
	 * Returns the values of every property of the block's state.
	 *
	 * @return the values in the block's property order
	 * @throws UnknownBlockNameException when Anvil ships no block data for the player's release
	 */
	public @NotNull Map<String, String> properties() {
		return requireIdentity().getProperties();
	}

	/**
	 * Tells whether the block is air: {@code air}, {@code cave_air} or {@code void_air}.
	 *
	 * @return whether the block is air
	 * @throws UnknownBlockNameException when Anvil ships no block data for the player's release
	 */
	public boolean isAir() {
		return requireIdentity().isAir();
	}

	/**
	 * Describes the block for a failure message, by its name where the release knows it.
	 *
	 * @return the name with its properties, or the state ID, and the position
	 */
	public @NotNull String describe() {
		String state = identity == null ? "state " + stateId : identity.describe();
		return state + " at " + x + " " + y + " " + z;
	}
}
