package me.whereareiam.anvil.capability.dialogs.nbt;

import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogOption;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogReaderTest {
	@Test
	void readsAMultiActionFormWithEveryInputType() {
		Map<String, Object> form = compound(
				"type", "minecraft:multi_action",
				"title", compound("text", "Identica", "extra", List.of(": Log in")),
				"body", compound("type", "minecraft:plain_message", "contents", "Welcome back"),
				"inputs", List.of(
						compound("type", "minecraft:text", "key", "password", "label", "Password", "max_length", 64),
						compound("type", "boolean", "key", "remember", "label", "Remember", "initial", (byte) 1, "on_true", "yes"),
						compound("type", "minecraft:single_option", "key", "mode", "label", "Mode", "options", List.of(
								compound("id", "easy", "display", "Easy"),
								compound("id", "hard", "display", "Hard", "initial", (byte) 1))),
						compound("type", "minecraft:number_range", "key", "amount", "label", "Amount", "start", 1F, "end", 4F, "step", 0.5F),
						compound("type", "example:colour", "key", "colour")),
				"actions", List.of(
						compound("label", "Log in", "action", compound("type", "minecraft:dynamic/run_command", "template", "login $(password)")),
						compound("label", "Help", "action", compound("type", "minecraft:dynamic/custom", "id", "example:help",
								"additions", compound("source", "dialog", "page", 2))),
						compound("label", "Rules", "action", compound("type", "minecraft:open_url", "url", "https://example.com/rules"))),
				"exit_action", compound("label", "Leave", "action", compound("type", "minecraft:run_command", "command", "/hub")),
				"can_close_with_escape", (byte) 0,
				"after_action", "wait_for_response"
		);

		assertEquals(Dialog.builder()
				.type("minecraft:multi_action")
				.title("Identica: Log in")
				.externalTitle("Identica: Log in")
				.body("Welcome back")
				.input(DialogInput.builder().key("password").type(DialogInputType.TEXT).label("Password").initial("").maxLength(64).build())
				.input(DialogInput.builder().key("remember").type(DialogInputType.BOOLEAN).label("Remember").initial("true")
						.onTrue("yes").onFalse("false").build())
				.input(DialogInput.builder().key("mode").type(DialogInputType.SINGLE_OPTION).label("Mode").initial("hard")
						.option(new DialogOption("easy", "Easy", false)).option(new DialogOption("hard", "Hard", true)).build())
				.input(DialogInput.builder().key("amount").type(DialogInputType.NUMBER_RANGE).label("Amount").initial("2.5")
						.start(1F).end(4F).step(0.5F).build())
				.input(DialogInput.builder().key("colour").type(DialogInputType.OTHER).label("").initial("").build())
				.button(new DialogButton("Log in", DialogAction.of(DialogActionType.DYNAMIC_RUN_COMMAND, "login $(password)", null), false))
				.button(new DialogButton("Help", DialogAction.of(DialogActionType.DYNAMIC_CUSTOM, "example:help", "{source:\"dialog\",page:2}"), false))
				.button(new DialogButton("Rules", DialogAction.of(DialogActionType.OPEN_URL, "https://example.com/rules", null), false))
				.button(new DialogButton("Leave", DialogAction.of(DialogActionType.RUN_COMMAND, "/hub", null), true))
				.closeWithEscape(false)
				.afterAction(DialogAfterAction.WAIT_FOR_RESPONSE)
				.build(), DialogReader.read(form));
	}

	@Test
	void readsTheDefaultsOfNoticesAndTheButtonsOfConfirmations() {
		Map<String, Object> notice = compound(
				"type", "minecraft:notice",
				"title", compound("translate", "menu.paused"),
				"body", List.of(
						compound("type", "minecraft:plain_message", "contents", "First"),
						compound("type", "minecraft:item", "item", compound("id", "minecraft:diamond"), "description", compound("contents", "Shiny")))
		);
		Map<String, Object> confirmation = compound(
				"type", "minecraft:confirmation",
				"title", "Leave?",
				"yes", compound("label", "Yes", "action", compound("type", "minecraft:custom", "id", "example:leave")),
				"no", compound("label", "No")
		);

		Dialog shownNotice = DialogReader.read(notice);
		Dialog shownConfirmation = DialogReader.read(confirmation);

		assertEquals("menu.paused", shownNotice.getTitle());
		assertEquals(List.of("First", "Shiny"), shownNotice.getBody());
		assertEquals(List.of(new DialogButton("gui.ok", null, true)), shownNotice.getButtons());
		assertTrue(shownNotice.isCloseWithEscape());
		assertEquals(DialogAfterAction.CLOSE, shownNotice.getAfterAction());
		assertEquals(List.of(
				new DialogButton("Yes", DialogAction.of(DialogActionType.CUSTOM, "example:leave", null), false),
				new DialogButton("No", null, true)
		), shownConfirmation.getButtons());
	}

	@Test
	void describesADialogShownByRegistryIdWithItsIdOnly() {
		Dialog dialog = DialogReader.registered(3);

		assertEquals(3, dialog.getRegistryId());
		assertEquals("", dialog.getType());
		assertEquals(List.of(), dialog.getButtons());
	}

	@Test
	void readsNestedDialogsAndTheEntriesOfADialogList() {
		Map<String, Object> rules = compound("type", "minecraft:notice", "title", "Rules", "external_title", "Server rules");
		Map<String, Object> list = compound(
				"type", "minecraft:dialog_list",
				"title", "Menu",
				"dialogs", List.of(rules),
				"exit_action", compound("label", "Back", "action", compound("type", "minecraft:show_dialog", "dialog", "example:main"))
		);
		Map<String, Object> tagged = compound("type", "minecraft:dialog_list", "title", "Extras", "dialogs", "#minecraft:pause_screen_additions");
		Map<String, Object> named = compound("type", "minecraft:dialog_list", "title", "Named", "dialogs", List.of("example:rules"));

		Dialog menu = DialogReader.read(list);

		Dialog nested = menu.getButtons().getFirst().getAction().getDialog();
		assertEquals("Server rules", menu.getButtons().getFirst().getLabel());
		assertEquals("Rules", nested.getTitle());
		assertEquals(new DialogButton("Back", DialogAction.show("example:main", null), true), menu.getButtons().get(1));
		assertEquals(List.of(), DialogReader.read(tagged).getButtons());
		assertEquals(List.of(new DialogButton("example:rules", DialogAction.show("example:rules", null), false)),
				DialogReader.read(named).getButtons());
	}

	@Test
	void readsAShowDialogActionWithTheDialogInline() {
		Map<String, Object> dialog = compound(
				"type", "minecraft:notice",
				"title", "Start",
				"action", compound("label", "Next", "action", compound("type", "show_dialog",
						"dialog", compound("type", "minecraft:notice", "title", "Second")))
		);

		DialogAction action = DialogReader.read(dialog).getButtons().getFirst().getAction();

		assertEquals("Second", action.getDialog().getTitle());
	}

	static Map<String, Object> compound(Object... entries) {
		Map<String, Object> compound = new LinkedHashMap<>();
		for (int index = 0; index < entries.length; index += 2) compound.put((String) entries[index], entries[index + 1]);
		return compound;
	}
}
