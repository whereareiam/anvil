package me.whereareiam.anvil.capability.protocol.api.model.nbt;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * An NBT list tag, whose elements share one tag type as they do on the wire. Minecraft writes a list of mixed
 * values by wrapping each element in a compound under the empty key; such a list is a list of compounds here too.
 *
 * <pre>{@code
 * NbtList pages = NbtList.of(NbtInt.of(1), NbtInt.of(2));
 * for (NbtTag page : pages.getElements()) read(page);
 * }</pre>
 */
@ToString
@EqualsAndHashCode
public final class NbtList implements NbtTag {
	/**
	 * The list without elements.
	 */
	public static final NbtList EMPTY = new NbtList(List.of());

	private final List<NbtTag> elements;

	private NbtList(List<NbtTag> elements) {
		this.elements = elements;
	}

	/**
	 * Creates a list of the given elements.
	 *
	 * @param elements the elements, all of one tag type
	 * @return the list
	 * @throws IllegalArgumentException when the elements are of more than one tag type
	 */
	public static @NotNull NbtList of(@NotNull List<? extends NbtTag> elements) {
		List<NbtTag> copy = List.copyOf(elements);
		for (NbtTag element : copy)
			if (element.getClass() != copy.getFirst().getClass())
				throw new IllegalArgumentException("An NBT list holds one tag type, but has " + copy.getFirst().getClass().getSimpleName()
						+ " and " + element.getClass().getSimpleName());
		return copy.isEmpty() ? EMPTY : new NbtList(copy);
	}

	/**
	 * Creates a list of the given elements.
	 *
	 * @param elements the elements, all of one tag type
	 * @return the list
	 * @throws IllegalArgumentException when the elements are of more than one tag type
	 */
	public static @NotNull NbtList of(@NotNull NbtTag... elements) {
		return of(List.of(elements));
	}

	/**
	 * Returns the elements in order.
	 *
	 * @return unmodifiable elements
	 */
	public @NotNull List<NbtTag> getElements() {
		return elements;
	}

	/**
	 * Returns the tag type the elements share.
	 *
	 * @return the element type, or {@code null} for the empty list
	 */
	public @Nullable Class<? extends NbtTag> getElementType() {
		return elements.isEmpty() ? null : elements.getFirst().getClass();
	}
}
