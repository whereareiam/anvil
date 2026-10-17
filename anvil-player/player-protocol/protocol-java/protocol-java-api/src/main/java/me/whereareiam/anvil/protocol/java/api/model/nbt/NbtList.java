package me.whereareiam.anvil.protocol.java.api.model.nbt;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A list tag. Since Minecraft 1.21.5 a list may hold tags of different types; the game writes such a list as a list
 * of compounds, and the library translation unwraps and wraps those elements, so the elements here are the tags
 * themselves.
 */
@Value
public class NbtList implements NbtTag {
	/**
	 * An empty list.
	 */
	public static final NbtList EMPTY = new NbtList(List.of());

	/**
	 * The elements in order.
	 */
	@NotNull List<NbtTag> elements;

	/**
	 * Creates the list from a copy of the elements.
	 *
	 * @param elements the elements in order
	 */
	public NbtList(@NotNull List<? extends NbtTag> elements) {
		this.elements = List.copyOf(elements);
	}

	/**
	 * Returns the elements of one tag type, leaving out the others.
	 *
	 * <pre>{@code
	 * List<NbtCompound> inputs = list.elements(NbtCompound.class);
	 * }</pre>
	 *
	 * @param type the tag type, such as {@code NbtCompound.class}
	 * @param <T> the tag type
	 * @return the elements of that type in order
	 */
	public <T extends NbtTag> @NotNull List<T> elements(@NotNull Class<T> type) {
		return elements.stream()
				.filter(type::isInstance)
				.map(type::cast)
				.toList();
	}
}
