package me.whereareiam.anvil.capability.dialogs.java;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByteArray;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtIntArray;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtList;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtLongArray;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtNumber;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Flattens a text component, as the game writes one into dialog NBT, to plain text, the way the protocol's other
 * text is flattened: text appears as written, keybinds and selectors as their names, and a translation as its key
 * followed by each argument after a space, since a client without the game's language files cannot translate it.
 * Score and NBT components and styles add nothing.
 *
 * <p>A component is a string, a list whose elements follow each other, or a compound whose content is the first of
 * {@code text}, {@code translate} with its {@code with} arguments, {@code score}, {@code selector}, {@code keybind}
 * and {@code nbt} it has, followed by its {@code extra} components. A number stands for its text, as a translation
 * argument may be one, and an array for its numbers one after another.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ComponentText {
	private static final String TEXT = "text";
	private static final String TRANSLATE = "translate";
	private static final String WITH = "with";
	private static final String SCORE = "score";
	private static final String SELECTOR = "selector";
	private static final String KEYBIND = "keybind";
	private static final String EXTRA = "extra";

	/**
	 * Flattens a component with its children.
	 *
	 * @param component the component, or {@code null} for none
	 * @return the component as plain text, empty for none
	 */
	static @NotNull String plain(@Nullable NbtTag component) {
		StringBuilder text = new StringBuilder();
		if (component != null) append(component, text);

		return text.toString();
	}

	private static void append(NbtTag component, StringBuilder text) {
		switch (component) {
			case NbtString value -> text.append(value.getValue());
			case NbtNumber value -> text.append(value.number());
			case NbtList list -> list.getElements().forEach(element -> append(element, text));
			case NbtCompound compound -> {
				appendContent(compound, text);
				compound.list(EXTRA).ifPresent(extra -> extra.getElements().forEach(child -> append(child, text)));
			}
			case NbtByteArray array -> {
				for (byte value : array.getValues()) text.append(value);
			}
			case NbtIntArray array -> {
				for (int value : array.getValues()) text.append(value);
			}
			case NbtLongArray array -> {
				for (long value : array.getValues()) text.append(value);
			}
		}
	}

	private static void appendContent(NbtCompound component, StringBuilder text) {
		NbtTag content = component.get(TEXT);
		if (content != null) {
			append(content, text);
			return;
		}

		NbtTag key = component.get(TRANSLATE);
		if (key != null) {
			appendTranslation(key, component, text);
			return;
		}
		if (component.contains(SCORE)) return;

		NbtTag selector = component.get(SELECTOR);
		if (selector != null) {
			append(selector, text);
			return;
		}

		NbtTag keybind = component.get(KEYBIND);
		if (keybind != null) append(keybind, text);
	}

	private static void appendTranslation(NbtTag key, NbtCompound component, StringBuilder text) {
		append(key, text);
		component.list(WITH).ifPresent(arguments -> arguments.getElements().forEach(argument -> {
			text.append(' ');
			append(argument, text);
		}));
	}
}
