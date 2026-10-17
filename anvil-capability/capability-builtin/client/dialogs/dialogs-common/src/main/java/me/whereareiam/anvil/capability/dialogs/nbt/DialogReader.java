package me.whereareiam.anvil.capability.dialogs.nbt;

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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a dialog from the NBT tree of the vanilla dialog codec, as Minecraft 1.21.6 introduced it, independent of
 * the protocol library that received it. Missing optional fields take the vanilla defaults: a dialog closes with
 * Escape and after an action, a notice has an {@code gui.ok} button, a text input accepts 32 characters and a slider
 * starts in the middle of its range. A dialog-list dialog gets one button per listed dialog, labelled with its
 * external title; a dialog it names from the registry is labelled with its registry name until the capability
 * resolves it, and a dialog tag lists nothing. Text is flattened to plain text, and custom payloads are written as
 * SNBT.
 *
 * <pre>{@code
 * Dialog dialog = DialogReader.read(showPacket.getDialog());
 * Dialog registered = DialogReader.registered(3);
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DialogReader {
	private static final String DEFAULT_NAMESPACE = "minecraft:";
	private static final int TEXT_MAX_LENGTH = 32;

	/**
	 * Reads a dialog the server sent in full.
	 *
	 * @param dialog the dialog compound as an NBT tree
	 * @return the dialog, shown in the game
	 */
	public static @NotNull Dialog read(@NotNull Map<String, Object> dialog) {
		return read(NbtCompound.of(dialog));
	}

	/**
	 * Describes a dialog the server showed by its registry ID, which the capability resolves against the registry
	 * it received during configuration.
	 *
	 * @param registryId index of the dialog in the dialog registry
	 * @return a dialog that carries only the registry ID
	 */
	public static @NotNull Dialog registered(int registryId) {
		return Dialog.builder()
				.type("")
				.registryId(registryId)
				.title("")
				.externalTitle("")
				.closeWithEscape(true)
				.afterAction(DialogAfterAction.CLOSE)
				.build();
	}

	private static Dialog read(NbtCompound dialog) {
		String type = id(dialog.string("type"));
		String title = ComponentText.plain(dialog.get("title"));
		Dialog.DialogBuilder builder = Dialog.builder()
				.type(type)
				.title(title)
				.externalTitle(dialog.has("external_title") ? ComponentText.plain(dialog.get("external_title")) : title)
				.closeWithEscape(dialog.bool("can_close_with_escape", true))
				.afterAction(afterAction(dialog.string("after_action", "close")));

		for (NbtCompound body : NbtCompound.compounds(dialog.get("body"))) builder.body(body(body));
		for (NbtCompound input : NbtCompound.compounds(dialog.get("inputs"))) builder.input(input(input));
		buttons(type, dialog).forEach(builder::button);
		return builder.build();
	}

	private static List<DialogButton> buttons(String type, NbtCompound dialog) {
		List<DialogButton> buttons = new ArrayList<>();
		switch (type) {
			case "minecraft:notice" -> buttons.add(dialog.has("action")
					? button(dialog.compound("action"), true)
					: new DialogButton("gui.ok", null, true));
			case "minecraft:confirmation" -> {
				buttons.add(button(dialog.compound("yes"), false));
				buttons.add(button(dialog.compound("no"), true));
			}
			case "minecraft:dialog_list" -> {
				buttons.addAll(listed(dialog.get("dialogs")));
				if (dialog.has("exit_action")) buttons.add(button(dialog.compound("exit_action"), true));
			}
			default -> {
				for (NbtCompound action : NbtCompound.compounds(dialog.get("actions"))) buttons.add(button(action, false));
				if (dialog.has("exit_action")) buttons.add(button(dialog.compound("exit_action"), true));
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
			if (!(entry instanceof Map<?, ?> nested)) continue;

			Dialog listed = read(NbtCompound.of(nested));
			buttons.add(new DialogButton(listed.getExternalTitle(), DialogAction.show("", listed), false));
		}
		return buttons;
	}

	private static List<?> entries(@Nullable Object dialogs) {
		if (dialogs == null) return List.of();
		if (dialogs instanceof List<?> list) return list;

		return List.of(dialogs);
	}

	private static DialogButton button(NbtCompound button, boolean exit) {
		NbtCompound action = button.compoundOrNull("action");
		return new DialogButton(ComponentText.plain(button.get("label")), action == null ? null : action(action), exit);
	}

	private static DialogAction action(NbtCompound action) {
		String type = id(action.string("type"));
		return switch (type) {
			case "minecraft:run_command" -> DialogAction.of(DialogActionType.RUN_COMMAND, action.string("command"), null);
			case "minecraft:suggest_command" -> DialogAction.of(DialogActionType.SUGGEST_COMMAND, action.string("command"), null);
			case "minecraft:open_url" -> DialogAction.of(DialogActionType.OPEN_URL, action.string("url"), null);
			case "minecraft:copy_to_clipboard" -> DialogAction.of(DialogActionType.COPY_TO_CLIPBOARD, action.string("value"), null);
			case "minecraft:change_page" -> DialogAction.of(DialogActionType.CHANGE_PAGE, Integer.toString(action.integer("page", 0)), null);
			case "minecraft:show_dialog" -> action.compoundOrNull("dialog") instanceof NbtCompound nested
					? DialogAction.show("", read(nested))
					: DialogAction.show(id(action.string("dialog")), null);
			case "minecraft:custom" -> DialogAction.of(DialogActionType.CUSTOM, action.string("id"), snbt(action.get("payload")));
			case "minecraft:dynamic/run_command" -> DialogAction.of(DialogActionType.DYNAMIC_RUN_COMMAND, action.string("template"), null);
			case "minecraft:dynamic/custom" -> DialogAction.of(DialogActionType.DYNAMIC_CUSTOM, action.string("id"), snbt(action.get("additions")));
			default -> DialogAction.of(DialogActionType.OTHER, type, null);
		};
	}

	private static String body(NbtCompound body) {
		if (id(body.string("type")).equals("minecraft:item")) {
			Object description = body.get("description");
			return description == null ? "" : message(description);
		}

		return ComponentText.plain(body.get("contents"));
	}

	/**
	 * Reads an item description, which is either a message with a width or only its text.
	 */
	private static String message(Object message) {
		if (message instanceof Map<?, ?> map && map.containsKey("contents")) return ComponentText.plain(map.get("contents"));
		return ComponentText.plain(message);
	}

	private static DialogInput input(NbtCompound input) {
		DialogInput.DialogInputBuilder builder = DialogInput.builder()
				.key(input.string("key"))
				.label(input.has("label") ? ComponentText.plain(input.get("label")) : "");

		switch (id(input.string("type"))) {
			case "minecraft:text" -> builder.type(DialogInputType.TEXT)
					.initial(input.string("initial", ""))
					.maxLength(input.integer("max_length", TEXT_MAX_LENGTH));
			case "minecraft:boolean" -> builder.type(DialogInputType.BOOLEAN)
					.initial(Boolean.toString(input.bool("initial", false)))
					.onTrue(input.string("on_true", "true"))
					.onFalse(input.string("on_false", "false"));
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
				float start = input.decimal("start", 0);
				float end = input.decimal("end", 0);
				builder.type(DialogInputType.NUMBER_RANGE)
						.start(start)
						.end(end)
						.step(input.has("step") ? input.decimal("step", 0) : null)
						.initial(number(input.decimal("initial", (start + end) / 2)));
			}
			default -> builder.type(DialogInputType.OTHER).initial("");
		}
		return builder.build();
	}

	private static List<DialogOption> options(@Nullable Object options) {
		List<DialogOption> read = new ArrayList<>();
		if (!(options instanceof List<?> list)) return read;

		for (Object option : list) {
			if (option instanceof String id) {
				read.add(new DialogOption(id, id, false));
				continue;
			}
			if (!(option instanceof Map<?, ?> map)) continue;

			NbtCompound compound = NbtCompound.of(map);
			String id = compound.string("id");
			read.add(new DialogOption(id, compound.has("display") ? ComponentText.plain(compound.get("display")) : id,
					compound.bool("initial", false)));
		}
		return read;
	}

	private static DialogAfterAction afterAction(String afterAction) {
		return DialogAfterAction.valueOf(afterAction.toUpperCase(Locale.ROOT));
	}

	private static String id(String id) {
		return id.contains(":") ? id : DEFAULT_NAMESPACE + id;
	}

	private static @Nullable String snbt(@Nullable Object tag) {
		return tag == null ? null : Snbt.write(tag);
	}

	private static String number(float value) {
		if (value == Math.rint(value) && !Float.isInfinite(value)) return Long.toString((long) value);
		return Float.toString(value);
	}
}
