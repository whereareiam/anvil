package me.whereareiam.anvil.capability.dialogs.nbt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Flattens a text component, as the vanilla codec writes it to NBT, to plain text: the content of text parts, the
 * key name of keybind parts and the pattern of selector parts and, for a translated part, its key followed by each
 * argument after a space. Score, NBT and object parts contribute nothing; children follow their parent.
 *
 * <p>A component is a string, a list whose elements follow each other, or a compound whose type is its
 * {@code type} entry or, without one, the first of its {@code text}, {@code translate}, {@code score},
 * {@code selector}, {@code keybind}, {@code nbt} and {@code object} entries. An element of a list of mixed types
 * is wrapped in a compound under the empty key, which is unwrapped.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ComponentText {
	private static final List<String> INFERRED = List.of("text", "translate", "score", "selector", "keybind", "nbt", "object");

	static @NotNull String plain(@Nullable Object component) {
		StringBuilder text = new StringBuilder();
		if (component != null) append(component, text);
		return text.toString();
	}

	private static void append(Object component, StringBuilder text) {
		switch (unwrap(component)) {
			case String value -> text.append(value);
			case Number value -> text.append(value);
			case List<?> parts -> parts.forEach(part -> append(part, text));
			case Map<?, ?> compound -> appendCompound(compound, text);
			default -> {
			}
		}
	}

	private static void appendCompound(Map<?, ?> component, StringBuilder text) {
		switch (type(component)) {
			case "text" -> append(component.get("text"), text);
			case "translatable" -> {
				text.append(component.get("translate") instanceof String key ? key : "");
				if (component.get("with") instanceof List<?> arguments)
					for (Object argument : arguments) {
						text.append(' ');
						append(argument, text);
					}
			}
			case "selector" -> append(component.get("selector"), text);
			case "keybind" -> append(component.get("keybind"), text);
			default -> {
			}
		}
		if (component.get("extra") instanceof List<?> children) children.forEach(child -> append(child, text));
	}

	private static String type(Map<?, ?> component) {
		if (component.get("type") instanceof String type) return type;

		for (String key : INFERRED) {
			if (!component.containsKey(key)) continue;

			return key.equals("translate") ? "translatable" : key;
		}
		return "";
	}

	private static Object unwrap(Object component) {
		if (component instanceof Map<?, ?> map && map.size() == 1 && map.containsKey("")) return unwrap(map.get(""));
		return component;
	}
}
