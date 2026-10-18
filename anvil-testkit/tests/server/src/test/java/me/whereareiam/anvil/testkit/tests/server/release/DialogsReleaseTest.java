package me.whereareiam.anvil.testkit.tests.server.release;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.capability.dialogs.DialogWatch;
import me.whereareiam.anvil.capability.dialogs.Dialogs;
import me.whereareiam.anvil.capability.dialogs.model.click.CommandConfirmation;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.messages.MessageWatch;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.testkit.tests.server.fixture.FixtureServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fills and presses the dialogs the server shows through the vanilla command, including the commands the client asks
 * about and a nested dialog.
 */
@Release
class DialogsReleaseTest {
	/**
	 * The release that added dialogs.
	 */
	private static final String DIALOGS = "1.21.6";

	@Test
	void pressesButtonsAndConfirmsTheCommandsTheClientAsksAbout(ScenarioContext anvil) {
		assumeTrue(TestedRelease.isAtLeast(DIALOGS), "Dialogs arrived in " + DIALOGS);
		SimulatedPlayer player = operator(anvil);
		Dialogs dialogs = player.capability(Dialogs.class);
		Messages messages = player.capability(Messages.class);

		messages.command("anvil-fixture dialog");
		Dialog dialog = dialogs.shown("anvil:dialog", Duration.ofSeconds(10));
		assertEquals(List.of("anvil:dialog-body"), dialog.getBody());
		assertEquals(List.of("Echo", "Nested", "Unknown", "Signed", "Seed", "Clear"),
				dialog.getButtons().stream().map(DialogButton::getLabel).toList());

		dialogs.press("Echo", Map.of("word", "hello", "flag", "true"));
		messages.received("anvil:dialog-echo:hello:on", Duration.ofSeconds(10));
		assertTrue(dialogs.current().isPresent());

		dialogs.press("Unknown");
		assertEquals(new CommandConfirmation("anvil-unknown-command", CommandCheck.PARSE_ERRORS),
				dialogs.confirmation().orElseThrow());
		dialogs.decline();
		assertEquals("anvil:dialog", dialogs.current().orElseThrow().getTitle());
		dialogs.press("Unknown");
		dialogs.confirm();
		messages.received("command.unknown.command", Duration.ofSeconds(10));

		dialogs.press("Signed");
		assertEquals(CommandCheck.SIGNATURE_REQUIRED, dialogs.confirmation().orElseThrow().getReason());
		MessageWatch afterSigned = messages.watch();
		dialogs.confirm();
		afterSigned.neverReceived("anvil:signed", Duration.ofSeconds(2));

		dialogs.press("Seed");
		assertEquals(CommandCheck.PERMISSIONS_REQUIRED, dialogs.confirmation().orElseThrow().getReason());
		dialogs.confirm();
		messages.received("commands.seed.success", Duration.ofSeconds(10));
	}

	@Test
	void opensANestedDialogAndClosesDialogs(ScenarioContext anvil) {
		assumeTrue(TestedRelease.isAtLeast(DIALOGS), "Dialogs arrived in " + DIALOGS);
		SimulatedPlayer player = operator(anvil);
		Dialogs dialogs = player.capability(Dialogs.class);
		Messages messages = player.capability(Messages.class);

		messages.command("anvil-fixture dialog");
		dialogs.shown("anvil:dialog", Duration.ofSeconds(10));
		dialogs.press("Nested");
		assertEquals("anvil:nested", dialogs.current().orElseThrow().getTitle());
		dialogs.close();
		dialogs.closed(Duration.ofSeconds(10));

		DialogWatch again = dialogs.watch();
		messages.command("anvil-fixture dialog");
		again.shown(shown -> shown.getTitle().equals("anvil:dialog"), Duration.ofSeconds(10));
		dialogs.press("Clear");
		dialogs.closed(Duration.ofSeconds(10));
	}

	@Test
	void hasNoDialogsBeforeTheyExisted(ScenarioContext anvil) {
		assumeFalse(TestedRelease.isAtLeast(DIALOGS), "Dialogs exist since " + DIALOGS);

		assertFalse(new FixtureServer(anvil).join().hasCapability(Dialogs.class));
	}

	/**
	 * Joins a player the fixture makes an operator, whose dialog buttons may run the seed command.
	 */
	private static SimulatedPlayer operator(ScenarioContext anvil) {
		SimulatedPlayer player = new FixtureServer(anvil).join();
		Messages messages = player.capability(Messages.class);
		messages.command("anvil-fixture op");
		messages.received("anvil:op", Duration.ofSeconds(10));

		return player;
	}
}
