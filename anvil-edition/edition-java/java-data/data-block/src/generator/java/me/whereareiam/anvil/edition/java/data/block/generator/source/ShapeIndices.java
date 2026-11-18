package me.whereareiam.anvil.edition.java.data.block.generator.source;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * The shape of a block's states in {@code blockCollisionShapes.json}: one shape index for every state, or one per
 * state.
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ShapeIndices {
	/**
	 * The indices as the file lists them: one, or one per state.
	 */
	@NotNull List<Integer> indices;
	/**
	 * Whether the file lists them as an array, one per state, rather than one number every state shares.
	 */
	boolean perState;

	/**
	 * Binds a block whose states all share one shape.
	 *
	 * @param index the shape's index
	 * @return the block's shapes
	 */
	@JsonCreator
	public static @NotNull ShapeIndices of(int index) {
		return new ShapeIndices(List.of(index), false);
	}

	/**
	 * Binds a block whose states each have a shape.
	 *
	 * @param indices the shape of each state, in state order
	 * @return the block's shapes
	 */
	@JsonCreator
	public static @NotNull ShapeIndices of(@NotNull List<Integer> indices) {
		return new ShapeIndices(List.copyOf(indices), true);
	}

	/**
	 * Returns the shape index of each of a block's states.
	 *
	 * @param stateCount the block's state count
	 * @return one index per state
	 * @throws IllegalStateException when the file lists shapes per state, but not one for each
	 */
	public @NotNull List<Integer> forStates(int stateCount) {
		if (!perState) return Collections.nCopies(stateCount, indices.getFirst());
		if (indices.size() != stateCount)
			throw new IllegalStateException("lists " + indices.size() + " collision shapes for " + stateCount + " states");

		return indices;
	}
}
