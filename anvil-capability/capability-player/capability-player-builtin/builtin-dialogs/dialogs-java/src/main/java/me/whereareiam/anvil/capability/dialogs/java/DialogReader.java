package me.whereareiam.anvil.capability.dialogs.java;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogOption;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtList;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtNumber;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a dialog from the NBT the vanilla dialog codec writes, as Minecraft 1.21.6 introduced it, into the dialogs
 * capability's {@link Dialog}, with its text flattened by {@link ComponentText}. The payload of each custom action
 * stays in the worker: the reader hands it to its {@link Retention} and the action carries only the handle it
 * returns.
 *
 * <p>Missing optional fields take the vanilla defaults: a dialog closes with Escape and after an action, a notice has
 * a {@code gui.ok} button, a text input accepts 32 characters and a slider starts in the middle of its range. A
 * dialog-list dialog gets one button per listed dialog, labelled with its external title; a dialog it names from the
 * registry is labelled with its registry name, and a dialog tag lists nothing. A confirmation's buttons are
 * {@code yes} and {@code no}; every other kind, including one this Anvil version does not know, has its
 * {@code actions} followed by its {@code exit_action}. An action or after-action of an unknown type reads as
 * {@link DialogActionType#OTHER} or {@link DialogAfterAction#OTHER}, and an input of an unknown type as
 * {@link DialogInputType#OTHER}.</p>
 */
@RequiredArgsConstructor
final class DialogReader {
	private static final String NAMESPACE_SEPARATOR = ":";
	private static final String DEFAULT_NAMESPACE = "minecraft" + NAMESPACE_SEPARATOR;
	private static final String TAG_PREFIX = "#";
	private static final String DEFAULT_BUTTON = "gui.ok";
	private static final int TEXT_MAX_LENGTH = 32;

	// Fields of the dialog codec.
	private static final String TYPE = "type";
	private static final String TITLE = "title";
	private static final String EXTERNAL_TITLE = "external_title";
	private static final String BODY = "body";
	private static final String INPUTS = "inputs";
	private static final String CAN_CLOSE_WITH_ESCAPE = "can_close_with_escape";
	private static final String AFTER_ACTION = "after_action";
	private static final String ACTION = "action";
	private static final String ACTIONS = "actions";
	private static final String EXIT_ACTION = "exit_action";
	private static final String YES = "yes";
	private static final String NO = "no";
	private static final String DIALOGS = "dialogs";
	private static final String LABEL = "label";
	private static final String CONTENTS = "contents";
	private static final String DESCRIPTION = "description";

	// Dialog types.
	private static final String NOTICE = "minecraft:notice";
	private static final String CONFIRMATION = "minecraft:confirmation";
	private static final String DIALOG_LIST = "minecraft:dialog_list";

	// Body types.
	private static final String ITEM = "minecraft:item";

	// Action types and their fields.
	private static final String RUN_COMMAND = "minecraft:run_command";
	private static final String SUGGEST_COMMAND = "minecraft:suggest_command";
	private static final String OPEN_URL = "minecraft:open_url";
	private static final String COPY_TO_CLIPBOARD = "minecraft:copy_to_clipboard";
	private static final String CHANGE_PAGE = "minecraft:change_page";
	private static final String SHOW_DIALOG = "minecraft:show_dialog";
	private static final String CUSTOM = "minecraft:custom";
	private static final String DYNAMIC_RUN_COMMAND = "minecraft:dynamic/run_command";
	private static final String DYNAMIC_CUSTOM = "minecraft:dynamic/custom";
	private static final String COMMAND = "command";
	private static final String URL = "url";
	private static final String VALUE = "value";
	private static final String PAGE = "page";
	private static final String DIALOG = "dialog";
	private static final String ID = "id";
	private static final String PAYLOAD = "payload";
	private static final String TEMPLATE = "template";
	private static final String ADDITIONS = "additions";

	// After-actions.
	private static final String CLOSE = "close";
	private static final String NONE = "none";
	private static final String WAIT_FOR_RESPONSE = "wait_for_response";

	// Input types and their fields.
	private static final String TEXT_INPUT = "minecraft:text";
	private static final String BOOLEAN_INPUT = "minecraft:boolean";
	private static final String SINGLE_OPTION_INPUT = "minecraft:single_option";
	private static final String NUMBER_RANGE_INPUT = "minecraft:number_range";
	private static final String KEY = "key";
	private static final String INITIAL = "initial";
	private static final String MAX_LENGTH = "max_length";
	private static final String ON_TRUE = "on_true";
	private static final String ON_FALSE = "on_false";
	private static final String OPTIONS = "options";
	private static final String DISPLAY = "display";
	private static final String START = "start";
	private static final String END = "end";
	private static final String STEP = "step";

	private final @NotNull Retention retention;

	/**
	 * Keeps the custom actions a reader reads.
	 */
	@FunctionalInterface
	interface Retention {
		/**
		 * Keeps a custom action.
		 *
		 * @param id the custom click's ID
		 * @param payload the payload or additions the action declared, or {@code null} for none
		 * @return the handle the capability's action carries
		 */
		long retain(@NotNull String id, @Nullable NbtTag payload);
	}

	/**
	 * Reads a dialog.
	 *
	 * @param dialog the dialog's NBT
	 * @return the capability's dialog
	 */
	@NotNull Dialog read(@NotNull NbtCompound dialog) {
		String type = id(string(dialog, TYPE, ""));
		String title = ComponentText.plain(dialog.get(TITLE));
		String externalTitle = dialog.contains(EXTERNAL_TITLE)
				? ComponentText.plain(dialog.get(EXTERNAL_TITLE))
				: title;
		Dialog.DialogBuilder builder = Dialog.builder()
				.type(type)
				.title(title)
				.externalTitle(externalTitle)
				.closeWithEscape(bool(dialog, CAN_CLOSE_WITH_ESCAPE, true))
				.afterAction(afterAction(string(dialog, AFTER_ACTION, CLOSE)));

		for (NbtCompound body : compounds(dialog.get(BODY))) builder.body(body(body));
		for (NbtCompound input : compounds(dialog.get(INPUTS))) builder.input(input(input));
		buttons(type, dialog).forEach(builder::button);

		return builder.build();
	}

	private List<DialogButton> buttons(String type, NbtCompound dialog) {
		List<DialogButton> buttons = new ArrayList<>();
		switch (type) {
			case NOTICE -> buttons.add(dialog.compound(ACTION)
					.map(action -> button(action, true))
					.orElseGet(() -> new DialogButton(DEFAULT_BUTTON, null, true)));
			case CONFIRMATION -> {
				dialog.compound(YES).ifPresent(yes -> buttons.add(button(yes, false)));
				dialog.compound(NO).ifPresent(no -> buttons.add(button(no, true)));
			}
			case DIALOG_LIST -> {
				buttons.addAll(listed(dialog.get(DIALOGS)));
				dialog.compound(EXIT_ACTION).ifPresent(exit -> buttons.add(button(exit, true)));
			}
			default -> {
				for (NbtCompound action : compounds(dialog.get(ACTIONS))) buttons.add(button(action, false));
				dialog.compound(EXIT_ACTION).ifPresent(exit -> buttons.add(button(exit, true)));
			}
		}

		return buttons;
	}

	/**
	 * Reads the dialogs of a dialog list, which is one dialog or tag, or a list of dialogs, each a registry name or
	 * the dialog itself.
	 */
	private List<DialogButton> listed(@Nullable NbtTag dialogs) {
		List<DialogButton> buttons = new ArrayList<>();
		if (dialogs instanceof NbtString name && name.getValue().startsWith(TAG_PREFIX)) return buttons;

		for (NbtTag entry : entries(dialogs)) {
			if (entry instanceof NbtString name) {
				String registryName = id(name.getValue());
				buttons.add(new DialogButton(registryName, DialogAction.show(registryName, null), false));
				continue;
			}
			if (!(entry instanceof NbtCompound inline)) continue;

			Dialog listed = read(inline);
			buttons.add(new DialogButton(listed.getExternalTitle(), DialogAction.show("", listed), false));
		}

		return buttons;
	}

	private static List<NbtTag> entries(@Nullable NbtTag dialogs) {
		if (dialogs == null) return List.of();
		if (dialogs instanceof NbtList list) return list.getElements();

		return List.of(dialogs);
	}

	private DialogButton button(NbtCompound button, boolean exit) {
		DialogAction action = button.compound(ACTION)
				.map(this::action)
				.orElse(null);
		return new DialogButton(ComponentText.plain(button.get(LABEL)), action, exit);
	}

	private DialogAction action(NbtCompound action) {
		String type = id(string(action, TYPE, ""));
		return switch (type) {
			case RUN_COMMAND -> carrying(DialogActionType.RUN_COMMAND, action, COMMAND);
			case SUGGEST_COMMAND -> carrying(DialogActionType.SUGGEST_COMMAND, action, COMMAND);
			case OPEN_URL -> carrying(DialogActionType.OPEN_URL, action, URL);
			case COPY_TO_CLIPBOARD -> carrying(DialogActionType.COPY_TO_CLIPBOARD, action, VALUE);
			case CHANGE_PAGE ->
					DialogAction.of(DialogActionType.CHANGE_PAGE, Integer.toString(integer(action, PAGE, 0)));
			case SHOW_DIALOG -> show(action.get(DIALOG));
			case CUSTOM -> custom(DialogActionType.CUSTOM, action, PAYLOAD);
			case DYNAMIC_RUN_COMMAND -> carrying(DialogActionType.DYNAMIC_RUN_COMMAND, action, TEMPLATE);
			case DYNAMIC_CUSTOM -> custom(DialogActionType.DYNAMIC_CUSTOM, action, ADDITIONS);
			default -> DialogAction.of(DialogActionType.OTHER, type);
		};
	}

	/**
	 * Reads an action whose value is the string in one of its fields.
	 */
	private static DialogAction carrying(DialogActionType type, NbtCompound action, String field) {
		return DialogAction.of(type, string(action, field, ""));
	}

	/**
	 * Reads a custom action, keeping its ID and the payload or additions in the given field.
	 */
	private DialogAction custom(DialogActionType type, NbtCompound action, String payloadField) {
		String id = string(action, ID, "");
		return DialogAction.custom(type, id, retention.retain(id, action.get(payloadField)));
	}

	/**
	 * Reads the dialog a show-dialog action opens: the dialog itself, or its registry name.
	 */
	private DialogAction show(@Nullable NbtTag dialog) {
		if (dialog instanceof NbtCompound inline) return DialogAction.show("", read(inline));
		if (dialog instanceof NbtString name) return DialogAction.show(id(name.getValue()), null);

		return DialogAction.show("", null);
	}

	private static String body(NbtCompound body) {
		if (!id(string(body, TYPE, "")).equals(ITEM)) return ComponentText.plain(body.get(CONTENTS));

		NbtTag description = body.get(DESCRIPTION);
		if (description == null) return "";

		return message(description);
	}

	/**
	 * Reads an item description, which is either a message with a width or only its text.
	 */
	private static String message(NbtTag message) {
		if (message instanceof NbtCompound compound && compound.contains(CONTENTS))
			return ComponentText.plain(compound.get(CONTENTS));

		return ComponentText.plain(message);
	}

	private static DialogInput input(NbtCompound input) {
		DialogInput.DialogInputBuilder builder = DialogInput.builder()
				.key(string(input, KEY, ""))
				.label(ComponentText.plain(input.get(LABEL)));

		switch (id(string(input, TYPE, ""))) {
			case TEXT_INPUT -> builder.type(DialogInputType.TEXT)
					.initial(string(input, INITIAL, ""))
					.maxLength(integer(input, MAX_LENGTH, TEXT_MAX_LENGTH));
			case BOOLEAN_INPUT -> builder.type(DialogInputType.BOOLEAN)
					.initial(Boolean.toString(bool(input, INITIAL, false)))
					.onTrue(string(input, ON_TRUE, Boolean.TRUE.toString()))
					.onFalse(string(input, ON_FALSE, Boolean.FALSE.toString()));
			case SINGLE_OPTION_INPUT -> {
				List<DialogOption> options = options(input.get(OPTIONS));
				builder.type(DialogInputType.SINGLE_OPTION).options(options).initial(options.stream()
						.filter(DialogOption::isInitial)
						.findFirst()
						.or(() -> options.stream().findFirst())
						.map(DialogOption::getId)
						.orElse(""));
			}
			case NUMBER_RANGE_INPUT -> {
				float start = decimal(input, START, 0);
				float end = decimal(input, END, 0);
				builder.type(DialogInputType.NUMBER_RANGE)
						.start(start)
						.end(end)
						.step(input.number(STEP).map(step -> step.number().floatValue()).orElse(null))
						.initial(number(decimal(input, INITIAL, (start + end) / 2)));
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

			String id = string(compound, ID, "");
			String display = compound.contains(DISPLAY) ? ComponentText.plain(compound.get(DISPLAY)) : id;
			read.add(new DialogOption(id, display, bool(compound, INITIAL, false)));
		}

		return read;
	}

	private static DialogAfterAction afterAction(String afterAction) {
		return switch (afterAction) {
			case CLOSE -> DialogAfterAction.CLOSE;
			case NONE -> DialogAfterAction.NONE;
			case WAIT_FOR_RESPONSE -> DialogAfterAction.WAIT_FOR_RESPONSE;
			default -> DialogAfterAction.OTHER;
		};
	}

	/**
	 * Reads a list or a single compound as a list of compounds, as the vanilla codec writes a one-element list.
	 */
	private static List<NbtCompound> compounds(@Nullable NbtTag value) {
		if (value instanceof NbtCompound compound) return List.of(compound);
		if (value instanceof NbtList list) return list.elements(NbtCompound.class);

		return List.of();
	}

	private static String string(NbtCompound compound, String key, String fallback) {
		return compound.string(key).map(NbtString::getValue).orElse(fallback);
	}

	private static boolean bool(NbtCompound compound, String key, boolean fallback) {
		return compound.number(key).map(NbtNumber::asBoolean).orElse(fallback);
	}

	private static int integer(NbtCompound compound, String key, int fallback) {
		return compound.number(key).map(value -> value.number().intValue()).orElse(fallback);
	}

	private static float decimal(NbtCompound compound, String key, float fallback) {
		return compound.number(key).map(value -> value.number().floatValue()).orElse(fallback);
	}

	private static String id(String id) {
		return id.contains(NAMESPACE_SEPARATOR) ? id : DEFAULT_NAMESPACE + id;
	}

	/**
	 * Writes a slider's number as the capability holds it, without a fraction when it is whole.
	 */
	private static String number(float value) {
		if (value == Math.rint(value) && !Float.isInfinite(value)) return Long.toString((long) value);

		return Float.toString(value);
	}
}
