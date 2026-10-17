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
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtList;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtNumber;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtString;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a dialog from the NBT of the vanilla dialog codec, as Minecraft 1.21.6 introduced it, independent of the
 * protocol library that received it. Missing optional fields take the vanilla defaults: a dialog closes with Escape
 * and after an action, a notice has an {@code gui.ok} button, a text input accepts 32 characters and a slider starts
 * in the middle of its range. A dialog-list dialog gets one button per listed dialog, labelled with its external
 * title; a dialog it names from the registry is labelled with its registry name until the capability resolves it,
 * and a dialog tag lists nothing. Text is flattened to plain text, and custom payloads are written as SNBT.
 *
 * <pre>{@code
 * Dialog dialog = DialogReader.read(compound);
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
	 * @param dialog the dialog compound
	 * @return the dialog, shown in the game
	 */
	public static @NotNull Dialog read(@NotNull NbtCompound dialog) {
		String type = id(string(dialog, "type", ""));
		String title = ComponentText.plain(dialog.get("title"));
		Dialog.DialogBuilder builder = Dialog.builder()
				.type(type)
				.title(title)
				.externalTitle(dialog.contains("external_title") ? ComponentText.plain(dialog.get("external_title")) : title)
				.closeWithEscape(bool(dialog, "can_close_with_escape", true))
				.afterAction(afterAction(string(dialog, "after_action", "close")));

		for (NbtCompound body : compounds(dialog.get("body"))) builder.body(body(body));
		for (NbtCompound input : compounds(dialog.get("inputs"))) builder.input(input(input));
		buttons(type, dialog).forEach(builder::button);
		return builder.build();
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

	private static List<DialogButton> buttons(String type, NbtCompound dialog) {
		List<DialogButton> buttons = new ArrayList<>();
		switch (type) {
			case "minecraft:notice" -> buttons.add(dialog.contains("action")
					? button(compound(dialog, "action"), true)
					: new DialogButton("gui.ok", null, true));
			case "minecraft:confirmation" -> {
				buttons.add(button(compound(dialog, "yes"), false));
				buttons.add(button(compound(dialog, "no"), true));
			}
			case "minecraft:dialog_list" -> {
				buttons.addAll(listed(dialog.get("dialogs")));
				if (dialog.contains("exit_action")) buttons.add(button(compound(dialog, "exit_action"), true));
			}
			default -> {
				for (NbtCompound action : compounds(dialog.get("actions"))) buttons.add(button(action, false));
				if (dialog.contains("exit_action")) buttons.add(button(compound(dialog, "exit_action"), true));
			}
		}
		return buttons;
	}

	/**
	 * Reads the dialogs of a dialog list, which is one dialog or tag, or a list of dialogs, each a registry name or
	 * the dialog itself.
	 */
	private static List<DialogButton> listed(@Nullable NbtTag dialogs) {
		List<DialogButton> buttons = new ArrayList<>();
		if (dialogs instanceof NbtString name && name.getValue().startsWith("#")) return buttons;

		for (NbtTag entry : entries(dialogs)) {
			if (entry instanceof NbtString name) {
				buttons.add(new DialogButton(id(name.getValue()), DialogAction.show(id(name.getValue()), null), false));
				continue;
			}
			if (!(entry instanceof NbtCompound nested)) continue;

			Dialog listed = read(nested);
			buttons.add(new DialogButton(listed.getExternalTitle(), DialogAction.show("", listed), false));
		}
		return buttons;
	}

	private static List<NbtTag> entries(@Nullable NbtTag dialogs) {
		if (dialogs == null) return List.of();
		if (dialogs instanceof NbtList list) return list.getElements();

		return List.of(dialogs);
	}

	private static DialogButton button(NbtCompound button, boolean exit) {
		NbtCompound action = button.getCompound("action");
		return new DialogButton(ComponentText.plain(button.get("label")), action == null ? null : action(action), exit);
	}

	private static DialogAction action(NbtCompound action) {
		String type = id(string(action, "type", ""));
		return switch (type) {
			case "minecraft:run_command" -> DialogAction.of(DialogActionType.RUN_COMMAND, string(action, "command", ""), null);
			case "minecraft:suggest_command" -> DialogAction.of(DialogActionType.SUGGEST_COMMAND, string(action, "command", ""), null);
			case "minecraft:open_url" -> DialogAction.of(DialogActionType.OPEN_URL, string(action, "url", ""), null);
			case "minecraft:copy_to_clipboard" -> DialogAction.of(DialogActionType.COPY_TO_CLIPBOARD, string(action, "value", ""), null);
			case "minecraft:change_page" -> DialogAction.of(DialogActionType.CHANGE_PAGE, Integer.toString(integer(action, "page", 0)), null);
			case "minecraft:show_dialog" -> action.getCompound("dialog") instanceof NbtCompound nested
					? DialogAction.show("", read(nested))
					: DialogAction.show(id(string(action, "dialog", "")), null);
			case "minecraft:custom" -> DialogAction.of(DialogActionType.CUSTOM, string(action, "id", ""), snbt(action.get("payload")));
			case "minecraft:dynamic/run_command" -> DialogAction.of(DialogActionType.DYNAMIC_RUN_COMMAND, string(action, "template", ""), null);
			case "minecraft:dynamic/custom" -> DialogAction.of(DialogActionType.DYNAMIC_CUSTOM, string(action, "id", ""), snbt(action.get("additions")));
			default -> DialogAction.of(DialogActionType.OTHER, type, null);
		};
	}

	private static String body(NbtCompound body) {
		if (id(string(body, "type", "")).equals("minecraft:item")) {
			NbtTag description = body.get("description");
			return description == null ? "" : message(description);
		}

		return ComponentText.plain(body.get("contents"));
	}

	/**
	 * Reads an item description, which is either a message with a width or only its text.
	 */
	private static String message(NbtTag message) {
		if (message instanceof NbtCompound compound && compound.contains("contents")) return ComponentText.plain(compound.get("contents"));
		return ComponentText.plain(message);
	}

	private static DialogInput input(NbtCompound input) {
		DialogInput.DialogInputBuilder builder = DialogInput.builder()
				.key(string(input, "key", ""))
				.label(input.contains("label") ? ComponentText.plain(input.get("label")) : "");

		switch (id(string(input, "type", ""))) {
			case "minecraft:text" -> builder.type(DialogInputType.TEXT)
					.initial(string(input, "initial", ""))
					.maxLength(integer(input, "max_length", TEXT_MAX_LENGTH));
			case "minecraft:boolean" -> builder.type(DialogInputType.BOOLEAN)
					.initial(Boolean.toString(bool(input, "initial", false)))
					.onTrue(string(input, "on_true", "true"))
					.onFalse(string(input, "on_false", "false"));
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
				float start = decimal(input, "start", 0);
				float end = decimal(input, "end", 0);
				builder.type(DialogInputType.NUMBER_RANGE)
						.start(start)
						.end(end)
						.step(input.contains("step") ? decimal(input, "step", 0) : null)
						.initial(number(decimal(input, "initial", (start + end) / 2)));
			}
			default -> builder.type(DialogInputType.OTHER).initial("");
		}
		return builder.build();
	}

	private static List<DialogOption> options(@Nullable NbtTag options) {
		List<DialogOption> read = new ArrayList<>();
		if (!(options instanceof NbtList list)) return read;

		for (NbtTag option : list.getElements()) {
			if (option instanceof NbtString id) {
				read.add(new DialogOption(id.getValue(), id.getValue(), false));
				continue;
			}
			if (!(option instanceof NbtCompound compound)) continue;

			String id = string(compound, "id", "");
			read.add(new DialogOption(id, compound.contains("display") ? ComponentText.plain(compound.get("display")) : id,
					bool(compound, "initial", false)));
		}
		return read;
	}

	/**
	 * Reads a list or a single compound as a list of compounds, as the vanilla codec writes a one-element list.
	 */
	private static List<NbtCompound> compounds(@Nullable NbtTag value) {
		if (value instanceof NbtCompound compound) return List.of(compound);
		if (!(value instanceof NbtList list)) return List.of();

		List<NbtCompound> compounds = new ArrayList<>();
		for (NbtTag element : list.getElements())
			if (element instanceof NbtCompound compound) compounds.add(compound);
		return compounds;
	}

	private static NbtCompound compound(NbtCompound compound, String key) {
		NbtCompound value = compound.getCompound(key);
		return value == null ? NbtCompound.EMPTY : value;
	}

	private static String string(NbtCompound compound, String key, String fallback) {
		String value = compound.getString(key);
		return value == null ? fallback : value;
	}

	private static boolean bool(NbtCompound compound, String key, boolean fallback) {
		return compound.get(key) instanceof NbtNumber value ? value.asNumber().byteValue() != 0 : fallback;
	}

	private static int integer(NbtCompound compound, String key, int fallback) {
		Number value = compound.getNumber(key);
		return value == null ? fallback : value.intValue();
	}

	private static float decimal(NbtCompound compound, String key, float fallback) {
		Number value = compound.getNumber(key);
		return value == null ? fallback : value.floatValue();
	}

	private static DialogAfterAction afterAction(String afterAction) {
		return DialogAfterAction.valueOf(afterAction.toUpperCase(Locale.ROOT));
	}

	private static String id(String id) {
		return id.contains(":") ? id : DEFAULT_NAMESPACE + id;
	}

	private static @Nullable String snbt(@Nullable NbtTag tag) {
		return tag == null ? null : tag.toSnbt();
	}

	private static String number(float value) {
		if (value == Math.rint(value) && !Float.isInfinite(value)) return Long.toString((long) value);
		return Float.toString(value);
	}
}
