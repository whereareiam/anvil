package me.whereareiam.anvil.capability.dialogs.java;

import me.whereareiam.anvil.capability.dialogs.java.ShownActions.CustomAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogOption;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByte;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtFloat;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtList;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogReaderTest {
	private final List<CustomAction> retained = new ArrayList<>();
	private final DialogReader reader = new DialogReader((id, payload) -> {
		retained.add(new CustomAction(id, payload));
		return retained.size();
	});

	@Test
	void readsAMultiActionFormWithEveryInputType() {
		NbtCompound form = compound(
				"type", text("minecraft:multi_action"),
				"title", compound("text", text("Identica"), "extra", list(text(": Log in"))),
				"body", compound("type", text("minecraft:plain_message"), "contents", text("Welcome back")),
				"inputs", list(
						compound("type", text("minecraft:text"), "key", text("password"), "label", text("Password"),
								"max_length", new NbtInt(64)),
						compound("type", text("boolean"), "key", text("remember"), "label", text("Remember"),
								"initial", NbtByte.of(true), "on_true", text("yes")),
						compound("type", text("minecraft:single_option"), "key", text("mode"), "label", text("Mode"),
								"options", list(
										compound("id", text("easy"), "display", text("Easy")),
										compound("id", text("hard"), "display", text("Hard"), "initial", NbtByte.of(true)))),
						compound("type", text("minecraft:number_range"), "key", text("amount"), "label", text("Amount"),
								"start", new NbtFloat(1), "end", new NbtFloat(4), "step", new NbtFloat(0.5F)),
						compound("type", text("example:colour"), "key", text("colour"))),
				"actions", list(
						compound("label", text("Log in"), "action", compound("type", text("minecraft:dynamic/run_command"),
								"template", text("login $(password)"))),
						compound("label", text("Help"), "action", compound("type", text("minecraft:dynamic/custom"),
								"id", text("example:help"),
								"additions", compound("source", text("dialog"), "page", new NbtInt(2)))),
						compound("label", text("Rules"), "action", compound("type", text("minecraft:open_url"),
								"url", text("https://example.com/rules")))),
				"exit_action", compound("label", text("Leave"), "action", compound("type", text("minecraft:run_command"),
						"command", text("/hub"))),
				"can_close_with_escape", NbtByte.of(false),
				"after_action", text("wait_for_response")
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
				.button(new DialogButton("Log in", DialogAction.of(DialogActionType.DYNAMIC_RUN_COMMAND, "login $(password)"), false))
				.button(new DialogButton("Help", DialogAction.custom(DialogActionType.DYNAMIC_CUSTOM, "example:help", 1), false))
				.button(new DialogButton("Rules", DialogAction.of(DialogActionType.OPEN_URL, "https://example.com/rules"), false))
				.button(new DialogButton("Leave", DialogAction.of(DialogActionType.RUN_COMMAND, "/hub"), true))
				.closeWithEscape(false)
				.afterAction(DialogAfterAction.WAIT_FOR_RESPONSE)
				.build(), reader.read(form));
		assertEquals(List.of(new CustomAction("example:help", compound("source", text("dialog"), "page", new NbtInt(2)))),
				retained);
	}

	@Test
	void readsTheDefaultsOfNoticesAndTheButtonsOfConfirmations() {
		NbtCompound notice = compound(
				"type", text("minecraft:notice"),
				"title", compound("translate", text("menu.paused")),
				"body", list(
						compound("type", text("minecraft:plain_message"), "contents", text("First")),
						compound("type", text("minecraft:item"), "item", compound("id", text("minecraft:diamond")),
								"description", compound("contents", text("Shiny"))))
		);
		NbtCompound confirmation = compound(
				"type", text("minecraft:confirmation"),
				"title", text("Leave?"),
				"yes", compound("label", text("Yes"), "action", compound("type", text("minecraft:custom"), "id", text("example:leave"))),
				"no", compound("label", text("No"))
		);

		Dialog shownNotice = reader.read(notice);
		Dialog shownConfirmation = reader.read(confirmation);

		assertEquals("menu.paused", shownNotice.getTitle());
		assertEquals(List.of("First", "Shiny"), shownNotice.getBody());
		assertEquals(List.of(new DialogButton("gui.ok", null, true)), shownNotice.getButtons());
		assertTrue(shownNotice.isCloseWithEscape());
		assertEquals(DialogAfterAction.CLOSE, shownNotice.getAfterAction());
		assertEquals(List.of(
				new DialogButton("Yes", DialogAction.custom(DialogActionType.CUSTOM, "example:leave", 1), false),
				new DialogButton("No", null, true)
		), shownConfirmation.getButtons());
	}

	@Test
	void readsNestedDialogsAndTheEntriesOfADialogList() {
		NbtCompound rules = compound("type", text("minecraft:notice"), "title", text("Rules"), "external_title", text("Server rules"));
		NbtCompound menu = compound(
				"type", text("minecraft:dialog_list"),
				"title", text("Menu"),
				"dialogs", list(rules),
				"exit_action", compound("label", text("Back"), "action", compound("type", text("minecraft:show_dialog"),
						"dialog", text("example:main")))
		);
		NbtCompound tagged = compound("type", text("minecraft:dialog_list"), "title", text("Extras"),
				"dialogs", text("#minecraft:pause_screen_additions"));
		NbtCompound named = compound("type", text("minecraft:dialog_list"), "title", text("Named"),
				"dialogs", list(text("example:rules")));
		NbtCompound opening = compound("type", text("minecraft:notice"), "title", text("Open"),
				"action", compound("label", text("Rules"), "action", compound("type", text("minecraft:show_dialog"), "dialog", rules)));

		Dialog list = reader.read(menu);

		Dialog nested = list.getButtons().getFirst().getAction().getDialog();
		assertEquals("Server rules", list.getButtons().getFirst().getLabel());
		assertEquals("Rules", nested.getTitle());
		assertEquals(new DialogButton("Back", DialogAction.show("example:main", null), true), list.getButtons().get(1));
		assertEquals(List.of(), reader.read(tagged).getButtons());
		assertEquals(List.of(new DialogButton("example:rules", DialogAction.show("example:rules", null), false)),
				reader.read(named).getButtons());
		assertEquals(DialogAction.show("", reader.read(rules)), reader.read(opening).getButtons().getFirst().getAction());
	}

	@Test
	void readsEveryActionTypeWithTheValueItCarriesAndKeepsCustomPayloadsInTheWorker() {
		NbtCompound actions = compound(
				"type", text("minecraft:multi_action"),
				"title", text("Actions"),
				"actions", list(
						button("Suggest", compound("type", text("suggest_command"), "command", text("/msg"))),
						button("Copy", compound("type", text("minecraft:copy_to_clipboard"), "value", text("code"))),
						button("Page", compound("type", text("minecraft:change_page"), "page", new NbtInt(3))),
						button("Ping", compound("type", text("minecraft:custom"), "id", text("example:ping"),
								"payload", compound("ping", NbtByte.of(true)))),
						button("Wave", compound("type", text("example:wave"))))
		);

		assertEquals(List.of(
				DialogAction.of(DialogActionType.SUGGEST_COMMAND, "/msg"),
				DialogAction.of(DialogActionType.COPY_TO_CLIPBOARD, "code"),
				DialogAction.of(DialogActionType.CHANGE_PAGE, "3"),
				DialogAction.custom(DialogActionType.CUSTOM, "example:ping", 1),
				DialogAction.of(DialogActionType.OTHER, "example:wave")
		), reader.read(actions).getButtons().stream().map(DialogButton::getAction).toList());
		assertEquals(List.of(new CustomAction("example:ping", compound("ping", NbtByte.of(true)))), retained);
	}

	@Test
	void readsUnknownKindsAndAfterActionsWithoutFailing() {
		NbtCompound unknown = compound(
				"type", text("example:wizard"),
				"title", text("Wizard"),
				"actions", list(button("Next", compound("type", text("minecraft:run_command"), "command", text("next")))),
				"after_action", text("example:linger")
		);

		Dialog dialog = reader.read(unknown);

		assertEquals("example:wizard", dialog.getType());
		assertEquals(List.of(new DialogButton("Next", DialogAction.of(DialogActionType.RUN_COMMAND, "next"), false)),
				dialog.getButtons());
		assertEquals(DialogAfterAction.OTHER, dialog.getAfterAction());
	}

	@Test
	void startsASliderInTheMiddleOfItsRangeAndWritesAWholeNumberWithoutAFraction() {
		NbtCompound slider = compound(
				"type", text("minecraft:notice"),
				"title", text("Volume"),
				"inputs", compound("type", text("minecraft:number_range"), "key", text("volume"),
						"start", new NbtInt(0), "end", new NbtInt(10))
		);

		DialogInput volume = reader.read(slider).getInputs().getFirst();

		assertEquals("5", volume.getInitial());
		assertEquals(0F, volume.getStart());
		assertEquals(10F, volume.getEnd());
		assertNull(volume.getStep());
	}

	private static NbtCompound button(String label, NbtCompound action) {
		return compound("label", text(label), "action", action);
	}

	private static NbtCompound compound(Object... entries) {
		Map<String, NbtTag> map = new LinkedHashMap<>();
		for (int index = 0; index < entries.length; index += 2) map.put((String) entries[index], (NbtTag) entries[index + 1]);

		return new NbtCompound(map);
	}

	private static NbtList list(NbtTag... elements) {
		return new NbtList(List.of(elements));
	}

	private static NbtString text(String value) {
		return new NbtString(value);
	}
}
