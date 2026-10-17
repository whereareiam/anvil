package me.whereareiam.anvil.capability.dialogs.screen;

import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogOption;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DialogPressTest {
	private static final List<DialogInput> INPUTS = List.of(
			DialogInput.builder().key("password").type(DialogInputType.TEXT).label("Password").initial("").maxLength(8).build(),
			DialogInput.builder().key("remember").type(DialogInputType.BOOLEAN).label("Remember").initial("false")
					.onTrue("yes").onFalse("no").build(),
			DialogInput.builder().key("amount").type(DialogInputType.NUMBER_RANGE).label("Amount").initial("5")
					.start(0F).end(10F).build(),
			DialogInput.builder().key("mode").type(DialogInputType.SINGLE_OPTION).label("Mode").initial("easy")
					.option(new DialogOption("easy", "Easy", true)).option(new DialogOption("hard", "Hard", false)).build(),
			DialogInput.builder().key("colour").type(DialogInputType.OTHER).label("").initial("").build()
	);

	@Test
	void fillsCommandTemplatesWithEveryKindOfValue() {
		DialogAction register = DialogAction.of(DialogActionType.DYNAMIC_RUN_COMMAND, "/register $(password) $(remember) $(amount) $(mode)");
		DialogAction set = DialogAction.of(DialogActionType.DYNAMIC_RUN_COMMAND, "set $(amount) $(remember)");

		assertEquals(DialogClick.command("register Pass 1$ yes 5 easy"),
				DialogPress.click(register, INPUTS, Map.of("password", "Pass 1$", "remember", "true")));
		assertEquals(DialogClick.command("set 2.5 no"), DialogPress.click(set, INPUTS, Map.of("amount", "2.5", "mode", "hard")));
	}

	@Test
	void runsStaticCommandsWithoutTheirSlash() {
		assertEquals(DialogClick.command("hub"), DialogPress.click(DialogAction.of(DialogActionType.RUN_COMMAND, "/hub"), INPUTS, Map.of()));
	}

	@Test
	void namesTheCustomActionAndSendsEveryInputValueWithADynamicOne() {
		DialogAction submit = DialogAction.custom(DialogActionType.DYNAMIC_CUSTOM, "example:submit", 4);
		DialogAction ping = DialogAction.custom(DialogActionType.CUSTOM, "example:ping", 5);

		assertEquals(DialogClick.custom(4, List.of(
				new DialogValue("password", DialogInputType.TEXT, "secret"),
				new DialogValue("remember", DialogInputType.BOOLEAN, "false"),
				new DialogValue("amount", DialogInputType.NUMBER_RANGE, "5"),
				new DialogValue("mode", DialogInputType.SINGLE_OPTION, "easy")
		)), DialogPress.click(submit, INPUTS, Map.of("password", "secret")));
		assertEquals(DialogClick.custom(5, List.of()), DialogPress.click(ping, INPUTS, Map.of()));
	}

	@Test
	void sendsNothingForClientSideActionsButtonsWithoutOneAndNestedDialogs() {
		assertNull(DialogPress.click(null, INPUTS, Map.of()));
		assertNull(DialogPress.click(DialogAction.of(DialogActionType.OPEN_URL, "https://example.com"), INPUTS, Map.of()));
		assertNull(DialogPress.click(DialogAction.show("example:rules", null), INPUTS, Map.of()));
	}

	@Test
	void refusesValuesTheInputsDoNotAccept() {
		DialogAction go = DialogAction.of(DialogActionType.DYNAMIC_RUN_COMMAND, "go $(missing)");

		assertEquals("The dialog has no input name; inputs: [password, remember, amount, mode, colour]",
				assertThrows(IllegalArgumentException.class, () -> DialogPress.click(go, INPUTS, Map.of("name", "Alice"))).getMessage());
		assertEquals("Input password accepts at most 8 characters",
				assertThrows(IllegalArgumentException.class, () -> DialogPress.click(go, INPUTS, Map.of("password", "too long secret"))).getMessage());
		assertEquals("Input remember accepts true or false, not yes",
				assertThrows(IllegalArgumentException.class, () -> DialogPress.click(go, INPUTS, Map.of("remember", "yes"))).getMessage());
		assertEquals("Input amount accepts numbers from 0.0 to 10.0, not 11",
				assertThrows(IllegalArgumentException.class, () -> DialogPress.click(go, INPUTS, Map.of("amount", "11"))).getMessage());
		assertEquals("Input mode has no option expert",
				assertThrows(IllegalArgumentException.class, () -> DialogPress.click(go, INPUTS, Map.of("mode", "expert"))).getMessage());
		assertEquals("Input colour has a type Anvil does not know",
				assertThrows(IllegalArgumentException.class, () -> DialogPress.click(go, INPUTS, Map.of("colour", "red"))).getMessage());
		assertEquals("The template go $(missing) names the input missing, which the dialog does not have",
				assertThrows(IllegalArgumentException.class, () -> DialogPress.click(go, INPUTS, Map.of())).getMessage());
		assertEquals("The custom action example:ping has no handle; only actions of a dialog the server showed can be pressed",
				assertThrows(IllegalArgumentException.class, () -> DialogPress.click(
						new DialogAction(DialogActionType.CUSTOM, "example:ping", null, null), INPUTS, Map.of())).getMessage());
		assertEquals("The button runs the action example:wave, which Anvil does not know",
				assertThrows(IllegalArgumentException.class,
						() -> DialogPress.click(DialogAction.of(DialogActionType.OTHER, "example:wave"), INPUTS, Map.of())).getMessage());
	}
}
