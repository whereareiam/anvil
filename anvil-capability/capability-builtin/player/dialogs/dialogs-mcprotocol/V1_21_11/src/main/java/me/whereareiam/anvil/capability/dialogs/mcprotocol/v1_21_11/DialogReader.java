package me.whereareiam.anvil.capability.dialogs.mcprotocol.v1_21_11;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogOption;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.KeybindComponent;
import net.kyori.adventure.text.SelectorComponent;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import org.cloudburstmc.nbt.NbtList;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.protocol.codec.NbtComponentSerializer;
import org.geysermc.mcprotocollib.protocol.data.DefaultComponentSerializer;
import org.geysermc.mcprotocollib.protocol.data.game.Holder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a dialog from the NBT the vanilla dialog codec writes, as Minecraft 1.21.6 introduced it. Missing optional
 * fields take the vanilla defaults: a dialog closes with Escape and after an action, a notice has an {@code gui.ok}
 * button, a text input accepts 32 characters and a slider starts in the middle of its range. A dialog-list dialog
 * gets one button per listed dialog, labelled with its external title; a dialog it names from the registry is
 * labelled with its registry name until the capability resolves it, and a dialog tag lists nothing.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class DialogReader {
	private static final String DEFAULT_NAMESPACE = "minecraft:";
	private static final int TEXT_MAX_LENGTH = 32;

	static Dialog read(Holder<NbtMap> holder) {
		if (holder.isId())
			return Dialog.builder()
					.type("")
					.registryId(holder.id())
					.title("")
					.externalTitle("")
					.closeWithEscape(true)
					.afterAction(DialogAfterAction.CLOSE)
					.build();

		return read(holder.custom());
	}

	static Dialog read(NbtMap dialog) {
		String type = id(dialog.getString("type"));
		String title = text(dialog.get("title"));
		Dialog.DialogBuilder builder = Dialog.builder()
				.type(type)
				.title(title)
				.externalTitle(dialog.containsKey("external_title") ? text(dialog.get("external_title")) : title)
				.closeWithEscape(dialog.getBoolean("can_close_with_escape", true))
				.afterAction(afterAction(dialog.getString("after_action", "close")));

		for (NbtMap body : compounds(dialog.get("body"))) builder.body(body(body));
		for (NbtMap input : compounds(dialog.get("inputs"))) builder.input(input(input));
		buttons(type, dialog).forEach(builder::button);
		return builder.build();
	}

	private static List<DialogButton> buttons(String type, NbtMap dialog) {
		List<DialogButton> buttons = new ArrayList<>();
		switch (type) {
			case "minecraft:notice" -> buttons.add(dialog.containsKey("action")
					? button(dialog.getCompound("action"), true)
					: new DialogButton("gui.ok", null, true));
			case "minecraft:confirmation" -> {
				buttons.add(button(dialog.getCompound("yes"), false));
				buttons.add(button(dialog.getCompound("no"), true));
			}
			case "minecraft:dialog_list" -> {
				buttons.addAll(listed(dialog.get("dialogs")));
				if (dialog.containsKey("exit_action")) buttons.add(button(dialog.getCompound("exit_action"), true));
			}
			default -> {
				for (NbtMap action : compounds(dialog.get("actions"))) buttons.add(button(action, false));
				if (dialog.containsKey("exit_action")) buttons.add(button(dialog.getCompound("exit_action"), true));
			}
		}
		return buttons;
	}

	/**
	 * Reads the dialogs of a dialog list, which is one dialog or tag, or a list of dialogs, each a registry name or
	 * the dialog itself.
	 */
	private static List<DialogButton> listed(@Nullable Object dialogs) {
		List<DialogButton> buttons = new ArrayList<>();
		if (dialogs instanceof String name && name.startsWith("#")) return buttons;

		for (Object entry : entries(dialogs)) {
			if (entry instanceof String name) {
				buttons.add(new DialogButton(id(name), DialogAction.show(id(name), null), false));
				continue;
			}

			Dialog listed = read((NbtMap) entry);
			buttons.add(new DialogButton(listed.getExternalTitle(), DialogAction.show("", listed), false));
		}
		return buttons;
	}

	private static List<?> entries(@Nullable Object dialogs) {
		if (dialogs == null) return List.of();
		if (dialogs instanceof NbtList<?> list) return list;

		return List.of(dialogs);
	}

	private static DialogButton button(NbtMap button, boolean exit) {
		NbtMap action = button.getCompound("action", null);
		return new DialogButton(text(button.get("label")), action == null ? null : action(action), exit);
	}

	private static DialogAction action(NbtMap action) {
		String type = id(action.getString("type"));
		return switch (type) {
			case "minecraft:run_command" -> DialogAction.of(DialogActionType.RUN_COMMAND, action.getString("command"), null);
			case "minecraft:suggest_command" -> DialogAction.of(DialogActionType.SUGGEST_COMMAND, action.getString("command"), null);
			case "minecraft:open_url" -> DialogAction.of(DialogActionType.OPEN_URL, action.getString("url"), null);
			case "minecraft:copy_to_clipboard" -> DialogAction.of(DialogActionType.COPY_TO_CLIPBOARD, action.getString("value"), null);
			case "minecraft:change_page" -> DialogAction.of(DialogActionType.CHANGE_PAGE, Integer.toString(action.getInt("page")), null);
			case "minecraft:show_dialog" -> action.get("dialog") instanceof NbtMap nested
					? DialogAction.show("", read(nested))
					: DialogAction.show(id(action.getString("dialog")), null);
			case "minecraft:custom" -> DialogAction.of(DialogActionType.CUSTOM, action.getString("id"), snbt(action.get("payload")));
			case "minecraft:dynamic/run_command" -> DialogAction.of(DialogActionType.DYNAMIC_RUN_COMMAND, action.getString("template"), null);
			case "minecraft:dynamic/custom" -> DialogAction.of(DialogActionType.DYNAMIC_CUSTOM, action.getString("id"), snbt(action.get("additions")));
			default -> DialogAction.of(DialogActionType.OTHER, type, null);
		};
	}

	private static String body(NbtMap body) {
		if (id(body.getString("type")).equals("minecraft:item")) {
			Object description = body.get("description");
			return description == null ? "" : message(description);
		}

		return text(body.get("contents"));
	}

	/**
	 * Reads an item description, which is either a message with a width or only its text.
	 */
	private static String message(Object message) {
		if (message instanceof NbtMap map && map.containsKey("contents")) return text(map.get("contents"));
		return text(message);
	}

	private static DialogInput input(NbtMap input) {
		DialogInput.DialogInputBuilder builder = DialogInput.builder()
				.key(input.getString("key"))
				.label(input.containsKey("label") ? text(input.get("label")) : "");

		switch (id(input.getString("type"))) {
			case "minecraft:text" -> builder.type(DialogInputType.TEXT)
					.initial(input.getString("initial", ""))
					.maxLength(input.getInt("max_length", TEXT_MAX_LENGTH));
			case "minecraft:boolean" -> builder.type(DialogInputType.BOOLEAN)
					.initial(Boolean.toString(input.getBoolean("initial", false)))
					.onTrue(input.getString("on_true", "true"))
					.onFalse(input.getString("on_false", "false"));
			case "minecraft:single_option" -> {
				List<DialogOption> options = options(input.get("options"));
				builder.type(DialogInputType.SINGLE_OPTION).options(options).initial(options.stream()
						.filter(DialogOption::isInitial)
						.findFirst()
						.or(() -> options.stream().findFirst())
						.map(DialogOption::getId)
						.orElse(""));
			}
			case "minecraft:number_range" -> {
				float start = input.getFloat("start");
				float end = input.getFloat("end");
				builder.type(DialogInputType.NUMBER_RANGE)
						.start(start)
						.end(end)
						.step(input.containsKey("step") ? input.getFloat("step") : null)
						.initial(number(input.getFloat("initial", (start + end) / 2)));
			}
			default -> builder.type(DialogInputType.OTHER).initial("");
		}
		return builder.build();
	}

	private static List<DialogOption> options(@Nullable Object options) {
		List<DialogOption> read = new ArrayList<>();
		if (!(options instanceof NbtList<?> list)) return read;

		for (Object option : list) {
			if (option instanceof String id) {
				read.add(new DialogOption(id, id, false));
				continue;
			}

			NbtMap map = (NbtMap) option;
			String id = map.getString("id");
			read.add(new DialogOption(id, map.containsKey("display") ? text(map.get("display")) : id, map.getBoolean("initial", false)));
		}
		return read;
	}

	private static DialogAfterAction afterAction(String afterAction) {
		return DialogAfterAction.valueOf(afterAction.toUpperCase(Locale.ROOT));
	}

	/**
	 * Reads a list or a single compound as a list of compounds, as the vanilla codec writes a one-element list.
	 */
	private static List<NbtMap> compounds(@Nullable Object value) {
		if (value instanceof NbtMap map) return List.of(map);
		if (!(value instanceof NbtList<?> list)) return List.of();

		List<NbtMap> compounds = new ArrayList<>();
		for (Object element : list) compounds.add((NbtMap) element);
		return compounds;
	}

	private static String id(String id) {
		return id.contains(":") ? id : DEFAULT_NAMESPACE + id;
	}

	private static @Nullable String snbt(@Nullable Object tag) {
		return tag == null ? null : NbtText.write(tag);
	}

	private static String number(float value) {
		if (value == Math.rint(value) && !Float.isInfinite(value)) return Long.toString((long) value);
		return Float.toString(value);
	}

	private static String text(@Nullable Object component) {
		if (component == null) return "";

		StringBuilder text = new StringBuilder();
		append(DefaultComponentSerializer.get().deserializeFromTree(NbtComponentSerializer.tagComponentToJson(component)), text);
		return text.toString();
	}

	/**
	 * Appends the content of text parts, the key name of keybind parts and the pattern of selector parts and, for a
	 * translated part, its key followed by each argument after a space. Score, NBT and object parts contribute nothing.
	 */
	private static void append(Component component, StringBuilder text) {
		if (component instanceof TextComponent value) text.append(value.content());
		if (component instanceof KeybindComponent value) text.append(value.keybind());
		if (component instanceof SelectorComponent value) text.append(value.pattern());
		if (component instanceof TranslatableComponent value) {
			text.append(value.key());
			for (TranslationArgument argument : value.arguments()) {
				text.append(' ');
				append(argument.asComponent(), text);
			}
		}
		for (Component child : component.children()) append(child, text);
	}
}
