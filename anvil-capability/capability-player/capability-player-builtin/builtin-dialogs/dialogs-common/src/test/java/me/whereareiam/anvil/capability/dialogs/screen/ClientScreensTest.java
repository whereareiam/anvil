package me.whereareiam.anvil.capability.dialogs.screen;

import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.capability.dialogs.model.click.CommandConfirmation;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.screen.ClientScreens.ConfirmScreen;
import me.whereareiam.anvil.capability.dialogs.screen.ClientScreens.DialogScreen;
import me.whereareiam.anvil.capability.dialogs.screen.ClientScreens.WaitingScreen;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientScreensTest {
	private static final Instant START = Instant.parse("2026-10-12T12:00:00Z");
	private static final Dialog FIRST = dialog("First");
	private static final Dialog SECOND = dialog("Second");

	@Test
	void showsADialogInPlaceOfTheOpenOneAndReturnsBelowIt() {
		ClientScreens screens = screens(START);
		screens.show(FIRST, null);
		DialogScreen first = assertInstanceOf(DialogScreen.class, screens.screen());

		screens.show(SECOND, first);

		assertEquals(new DialogScreen(SECOND, null), screens.screen());
		screens.clear();
		assertNull(screens.screen());
	}

	@Test
	void clearsOnlyDialogsAndResetsEveryScreen() {
		ClientScreens screens = screens(START);
		screens.show(FIRST, null);
		WaitingScreen waiting = assertInstanceOf(WaitingScreen.class, screens.after((DialogScreen) screens.screen(), DialogAfterAction.WAIT_FOR_RESPONSE));
		screens.set(waiting);

		screens.clear();
		assertSame(waiting, screens.screen());

		screens.show(SECOND, waiting);
		assertEquals(new DialogScreen(SECOND, null), screens.screen());
		screens.reset();
		assertNull(screens.screen());
	}

	@Test
	void leadsButtonsToTheDialogTheScreenBelowOrTheWaitingScreen() {
		ClientScreens screens = screens(START);
		DialogScreen open = new DialogScreen(SECOND, new DialogScreen(FIRST, null));

		assertSame(open, screens.after(open, DialogAfterAction.NONE));
		assertSame(open.previous(), screens.after(open, DialogAfterAction.CLOSE));
		assertEquals(new WaitingScreen(open.previous(), START), screens.after(open, DialogAfterAction.WAIT_FOR_RESPONSE));
	}

	@Test
	void refusesAnAfterActionAnvilDoesNotKnow() {
		DialogScreen open = new DialogScreen(SECOND, null);

		assertThrows(IllegalArgumentException.class, () -> screens(START).after(open, DialogAfterAction.OTHER));
	}

	@Test
	void confirmsOverTheOpenScreen() {
		ClientScreens screens = screens(START);
		screens.show(FIRST, null);
		DialogScreen open = (DialogScreen) screens.screen();

		screens.confirm("op Bob", CommandCheck.PERMISSIONS_REQUIRED, null);

		assertEquals(new ConfirmScreen(new CommandConfirmation("op Bob", CommandCheck.PERMISSIONS_REQUIRED), null, open), screens.screen());
		assertNull(screens.dialog());
	}

	@Test
	void enablesTheWaitingScreensBackButtonAfterFiveSeconds() {
		WaitingScreen waiting = new WaitingScreen(null, START);

		assertFalse(screens(START.plusSeconds(4)).canLeave(waiting));
		assertTrue(screens(START.plus(ClientScreens.WAITING_BACK_DELAY)).canLeave(waiting));
	}

	private static ClientScreens screens(Instant now) {
		return new ClientScreens(Clock.fixed(now, ZoneOffset.UTC));
	}

	private static Dialog dialog(String title) {
		return Dialog.builder()
				.type("minecraft:notice")
				.title(title)
				.externalTitle(title)
				.closeWithEscape(true)
				.afterAction(DialogAfterAction.CLOSE)
				.build();
	}
}
