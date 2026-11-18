package me.whereareiam.anvil.edition.java.data.block.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.edition.java.data.block.model.movement.BlockMovement;
import me.whereareiam.anvil.edition.java.data.block.type.BlockKind;
import me.whereareiam.anvil.edition.java.data.block.type.CollisionKind;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One block of a protocol's registry, such as {@code minecraft:stone}, with what vanilla's movement code needs from it.
 *
 * <p>Its states have the consecutive global IDs {@link #getMinStateId()} to {@link #getMaxStateId()}. The name,
 * properties and states come from PrismarineJS minecraft-data; the kind, the collision kind and the movement are
 * vanilla's facts that Anvil maintains by hand, and keep vanilla's defaults for every block that does not change
 * them.</p>
 *
 * <pre>{@code
 * BlockType block = registry.state(stateId).getBlock();
 * float friction = block.getMovement().getFriction();
 * boolean climbable = block.getMovement().has(MovementBehaviour.CLIMBABLE);
 * boolean fence = block.getKind() == BlockKind.FENCE;
 * }</pre>
 */
@Value
@Builder
public class BlockType {
	/**
	 * The block's numeric ID in the protocol's block registry.
	 */
	int id;
	/**
	 * The namespaced name, such as {@code minecraft:oak_stairs}.
	 */
	@NotNull String name;
	/**
	 * The global ID of the block's first state.
	 */
	int minStateId;
	/**
	 * The global ID of the block's last state.
	 */
	int maxStateId;
	/**
	 * The global ID of the state a newly placed block has.
	 */
	int defaultStateId;
	/**
	 * The state properties, in the order that numbers the states.
	 */
	@Singular
	@NotNull List<BlockProperty> properties;
	/**
	 * What the block is, where vanilla's movement code asks by its class or tag.
	 */
	@NotNull BlockKind kind;
	/**
	 * How vanilla decides the collision shape of the block's states.
	 */
	@NotNull CollisionKind collision;
	/**
	 * How the block changes the movement of an entity on or in it.
	 */
	@NotNull BlockMovement movement;

	/**
	 * Returns the property values of one of the block's states. Vanilla numbers a block's states by its properties,
	 * the last property varying fastest between consecutive state IDs.
	 *
	 * @param stateId global ID of one of the block's states
	 * @return the value of each property, in the block's property order
	 * @throws IllegalArgumentException when the state is not the block's
	 */
	public @NotNull Map<String, String> propertiesAt(int stateId) {
		if (stateId < minStateId || stateId > maxStateId)
			throw new IllegalArgumentException(
					name + " has the states " + minStateId + " to " + maxStateId + ", not " + stateId);

		String[] values = new String[properties.size()];
		int remainder = stateId - minStateId;
		for (int index = properties.size() - 1; index >= 0; index--) {
			List<String> choices = properties.get(index).getValues();
			values[index] = choices.get(remainder % choices.size());
			remainder /= choices.size();
		}

		Map<String, String> named = new LinkedHashMap<>();
		for (int index = 0; index < values.length; index++)
			named.put(properties.get(index).getName(), values[index]);
		return Collections.unmodifiableMap(named);
	}

	/**
	 * The number of states the block has, the product of its properties' value counts.
	 *
	 * @return the state count, at least one
	 */
	public int stateCount() {
		return maxStateId - minStateId + 1;
	}
}
