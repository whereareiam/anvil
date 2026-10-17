package me.whereareiam.anvil.capability.dialogs.java;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.dialogs.model.DialogRegistry;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogOption;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Maps the dialogs of the Java Edition protocol to the dialogs capability's own values. The protocol's dialog types
 * share their names with the capability's, so they are named in full here.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProtocolDialogs {
	/**
	 * Maps a shown dialog. A dialog the server refers to by registry ID carries only that ID, with an empty title and
	 * the default closing behaviour, until the host resolves it from the registry.
	 *
	 * @param packet the show-dialog packet
	 * @return the dialog, marked when the server showed it during configuration
	 */
	static @NotNull Dialog shown(@NotNull ClientboundShowDialog packet) {
		Dialog.DialogBuilder shown = packet.getDialog() == null
				? reference(packet.getRegistryId())
				: dialog(packet.getDialog()).toBuilder();
		return shown.configuration(packet.isConfiguration()).build();
	}

	/**
	 * Maps the dialog registry, keeping its order, so registry IDs still index it.
	 *
	 * @param packet the registry data packet
	 * @return the registry
	 */
	static @NotNull DialogRegistry registry(@NotNull ClientboundRegistryData packet) {
		List<RegistryDialog> dialogs = packet.getDialogs().stream()
				.map(entry -> new RegistryDialog(entry.getName(), entry.getDialog() == null
						? null
						: dialog(entry.getDialog())))
				.toList();
		return new DialogRegistry(dialogs);
	}

	private static Dialog.DialogBuilder reference(@Nullable Integer registryId) {
		return Dialog.builder()
				.type("")
				.registryId(registryId)
				.title("")
				.externalTitle("")
				.closeWithEscape(true)
				.afterAction(DialogAfterAction.CLOSE);
	}

	private static Dialog dialog(me.whereareiam.anvil.protocol.java.api.model.dialog.Dialog dialog) {
		Dialog.DialogBuilder builder = Dialog.builder()
				.type(dialog.getType())
				.title(dialog.getTitle())
				.externalTitle(dialog.getExternalTitle())
				.closeWithEscape(dialog.isCloseWithEscape())
				.afterAction(afterAction(dialog.getAfterAction()));
		dialog.getBody().forEach(builder::body);
		dialog.getInputs().forEach(input -> builder.input(input(input)));
		dialog.getButtons().forEach(button -> builder.button(button(button)));
		return builder.build();
	}

	private static DialogButton button(me.whereareiam.anvil.protocol.java.api.model.dialog.DialogButton button) {
		DialogAction action = button.getAction() == null ? null : action(button.getAction());
		return new DialogButton(button.getLabel(), action, button.isExit());
	}

	private static DialogAction action(me.whereareiam.anvil.protocol.java.api.model.dialog.DialogAction action) {
		Dialog dialog = action.getDialog() == null ? null : dialog(action.getDialog());
		return new DialogAction(actionType(action.getType()), action.getValue(), action.getPayload(), dialog);
	}

	private static DialogInput input(me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInput input) {
		DialogInput.DialogInputBuilder builder = DialogInput.builder()
				.key(input.getKey())
				.type(inputType(input.getType()))
				.label(input.getLabel())
				.initial(input.getInitial())
				.maxLength(input.getMaxLength())
				.onTrue(input.getOnTrue())
				.onFalse(input.getOnFalse())
				.start(input.getStart())
				.end(input.getEnd())
				.step(input.getStep());
		input.getOptions().forEach(option -> builder.option(option(option)));
		return builder.build();
	}

	private static DialogOption option(me.whereareiam.anvil.protocol.java.api.model.dialog.DialogOption option) {
		return new DialogOption(option.getId(), option.getDisplay(), option.isInitial());
	}

	private static DialogActionType actionType(me.whereareiam.anvil.protocol.java.api.type.DialogActionType type) {
		return switch (type) {
			case RUN_COMMAND -> DialogActionType.RUN_COMMAND;
			case DYNAMIC_RUN_COMMAND -> DialogActionType.DYNAMIC_RUN_COMMAND;
			case CUSTOM -> DialogActionType.CUSTOM;
			case DYNAMIC_CUSTOM -> DialogActionType.DYNAMIC_CUSTOM;
			case SUGGEST_COMMAND -> DialogActionType.SUGGEST_COMMAND;
			case OPEN_URL -> DialogActionType.OPEN_URL;
			case COPY_TO_CLIPBOARD -> DialogActionType.COPY_TO_CLIPBOARD;
			case CHANGE_PAGE -> DialogActionType.CHANGE_PAGE;
			case SHOW_DIALOG -> DialogActionType.SHOW_DIALOG;
			case OTHER -> DialogActionType.OTHER;
		};
	}

	private static DialogAfterAction afterAction(
			me.whereareiam.anvil.protocol.java.api.type.DialogAfterAction action
	) {
		return switch (action) {
			case CLOSE -> DialogAfterAction.CLOSE;
			case NONE -> DialogAfterAction.NONE;
			case WAIT_FOR_RESPONSE -> DialogAfterAction.WAIT_FOR_RESPONSE;
		};
	}

	private static DialogInputType inputType(me.whereareiam.anvil.protocol.java.api.type.DialogInputType type) {
		return switch (type) {
			case TEXT -> DialogInputType.TEXT;
			case BOOLEAN -> DialogInputType.BOOLEAN;
			case SINGLE_OPTION -> DialogInputType.SINGLE_OPTION;
			case NUMBER_RANGE -> DialogInputType.NUMBER_RANGE;
			case OTHER -> DialogInputType.OTHER;
		};
	}
}
