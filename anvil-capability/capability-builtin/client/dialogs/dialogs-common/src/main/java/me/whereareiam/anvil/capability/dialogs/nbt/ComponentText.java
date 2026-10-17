package me.whereareiam.anvil.capability.dialogs.nbt;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtList;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtNumber;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtString;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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

	static @NotNull String plain(@Nullable NbtTag component) {
		StringBuilder text = new StringBuilder();
		if (component != null) append(component, text);
		return text.toString();
	}

	private static void append(@Nullable NbtTag component, StringBuilder text) {
		switch (unwrap(component)) {
			case NbtString value -> text.append(value.getValue());
			case NbtNumber value -> text.append(value.asNumber());
			case NbtList parts -> parts.getElements().forEach(part -> append(part, text));
			case NbtCompound compound -> appendCompound(compound, text);
			case null, default -> {
			}
		}
	}

	private static void appendCompound(NbtCompound component, StringBuilder text) {
		switch (type(component)) {
			case "text" -> append(component.get("text"), text);
			case "translatable" -> {
				String key = component.getString("translate");
				text.append(key == null ? "" : key);
				NbtList arguments = component.getList("with");
				if (arguments != null)
					for (NbtTag argument : arguments.getElements()) {
						text.append(' ');
						append(argument, text);
					}
			}
			case "selector" -> append(component.get("selector"), text);
			case "keybind" -> append(component.get("keybind"), text);
			default -> {
			}
		}
		NbtList children = component.getList("extra");
		if (children != null) children.getElements().forEach(child -> append(child, text));
	}

	private static String type(NbtCompound component) {
		String type = component.getString("type");
		if (type != null) return type;

		for (String key : INFERRED) {
			if (!component.contains(key)) continue;

			return key.equals("translate") ? "translatable" : key;
		}
		return "";
	}

	private static @Nullable NbtTag unwrap(@Nullable NbtTag component) {
		if (component instanceof NbtCompound compound && compound.getEntries().size() == 1 && compound.contains("")) return unwrap(compound.get(""));
		return component;
	}
}
