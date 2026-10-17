package me.whereareiam.anvil.capability.dialogs;

import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.commands.Commands;
import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.capability.dialogs.channel.DialogsChannel;
import me.whereareiam.anvil.capability.dialogs.model.DialogRegistry;
import me.whereareiam.anvil.capability.dialogs.model.click.CommandConfirmation;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogOption;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import me.whereareiam.anvil.capability.dialogs.screen.ClientScreens;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelDialogsTest {
	private static final Duration TIMEOUT = Duration.ofSeconds(3);

	@Test
	void keepsEveryShownDialogAndTheOpenOneUntilTheServerClearsIt() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		assertEquals(Optional.empty(), dialogs.current());

		Dialog first = notice("First");
		Dialog second = notice("Second");
		channel.publish(DialogsChannel.SHOWN, first);
		channel.publish(DialogsChannel.SHOWN, second);

		assertEquals(Optional.of(second), dialogs.current());
		assertEquals(List.of(first, second), dialogs.history());
		assertThrows(UnsupportedOperationException.class, () -> dialogs.history().add(first));

		channel.publish(DialogsChannel.CLEARED, null);
		assertEquals(Optional.empty(), dialogs.current());
		dialogs.closed(TIMEOUT);
	}

	@Test
	void waitsForTheOpenDialogByTitle() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		channel.publish(DialogsChannel.SHOWN, notice("Identica: Log in"));

		assertEquals("Identica: Log in", dialogs.shown("Log in").getTitle());
		assertEquals("Player did not show a dialog titled 'Register'",
				assertThrows(IllegalStateException.class, () -> dialogs.shown("Register", TIMEOUT)).getMessage());
		assertEquals("Player did not close its dialog", assertThrows(IllegalStateException.class, () -> dialogs.closed(TIMEOUT)).getMessage());
	}

	@Test
	void watchesOnlyDialogsShownSinceItAndFailsOnAForbiddenOne() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		channel.publish(DialogsChannel.SHOWN, notice("Log in"));
		DialogWatch shown = dialogs.watch();
		Predicate<Dialog> login = dialog -> dialog.getTitle().equals("Log in");
		Expectation again = shown.expectShown(login);

		shown.neverShown(login, TIMEOUT);
		assertFalse(again.met());
		assertEquals("Player did not show a matching dialog",
				assertThrows(IllegalStateException.class, () -> shown.shown(login, TIMEOUT)).getMessage());

		Dialog second = notice("Log in");
		channel.publish(DialogsChannel.SHOWN, second);
		assertTrue(again.met());
		assertEquals(List.of(second), shown.unread());
		assertEquals("Player was shown a dialog it must not be shown: Log in",
				assertThrows(IllegalStateException.class, () -> shown.neverShown(login, TIMEOUT)).getMessage());
		assertSame(second, shown.shown(login, TIMEOUT));
		assertEquals(List.of(), shown.unread());
		shown.neverShown(login, TIMEOUT);
	}

	@Test
	void runsADynamicCommandWithTheInputsFilledAndClosesTheDialog() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		channel.publish(DialogsChannel.SHOWN, form(DialogAction.of(DialogActionType.DYNAMIC_RUN_COMMAND,
				"/register $(password) $(remember) $(amount) $(mode)"), DialogAfterAction.CLOSE));

		dialogs.press("Submit", Map.of("password", "Pass 1$", "remember", "true"));

		assertEquals(List.of(DialogClick.command("register Pass 1$ yes 5 easy")), channel.payloads);
		assertEquals(Optional.empty(), dialogs.current());
	}

	@Test
	void refusesButtonsItCannotPress() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		assertEquals("Player has no dialog open", assertThrows(IllegalStateException.class, () -> dialogs.press("Submit")).getMessage());

		channel.publish(DialogsChannel.SHOWN, form(DialogAction.show("example:missing", null), DialogAfterAction.CLOSE));
		assertEquals("The open dialog 'Form' has no button 'Cancel'; buttons: [Submit]",
				assertThrows(IllegalStateException.class, () -> dialogs.press("Cancel")).getMessage());
		assertEquals("The button opens the registry dialog example:missing, which the server did not send",
				assertThrows(IllegalArgumentException.class, () -> dialogs.press("Submit")).getMessage());
	}

	@Test
	void closesWithEscapeByPressingTheExitButton() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		Dialog confirmation = Dialog.builder()
				.type("minecraft:confirmation")
				.title("Leave?")
				.externalTitle("Leave?")
				.button(new DialogButton("Yes", DialogAction.of(DialogActionType.RUN_COMMAND, "/leave"), false))
				.button(new DialogButton("No", DialogAction.of(DialogActionType.RUN_COMMAND, "stay"), true))
				.closeWithEscape(true)
				.afterAction(DialogAfterAction.NONE)
				.build();
		channel.publish(DialogsChannel.SHOWN, confirmation);

		dialogs.close();

		assertEquals(List.of(DialogClick.command("stay")), channel.payloads);
		assertEquals(Optional.empty(), dialogs.current());
	}

	@Test
	void refusesToCloseADialogThatEscapeCannotClose() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		channel.publish(DialogsChannel.SHOWN, form(DialogAction.of(DialogActionType.RUN_COMMAND, "go"), DialogAfterAction.CLOSE));

		assertEquals("The open dialog 'Form' cannot be closed with Escape",
				assertThrows(IllegalStateException.class, dialogs::close).getMessage());
		assertEquals(List.of(), channel.payloads);
	}

	@Test
	void keepsADialogTheServerShowedWhileAButtonWasPressed() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		Dialog next = notice("Next");
		channel.onRequest = () -> channel.publish(DialogsChannel.SHOWN, next);
		channel.publish(DialogsChannel.SHOWN, form(DialogAction.of(DialogActionType.RUN_COMMAND, "go"), DialogAfterAction.CLOSE));

		dialogs.press("Submit");

		assertEquals(Optional.of(next), dialogs.current());
	}

	@Test
	void asksBeforeACommandTheClientCannotCheckAndRunsItWhenConfirmed() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		Dialog form = form(DialogAction.of(DialogActionType.RUN_COMMAND, "/missing command"), DialogAfterAction.CLOSE);
		channel.publish(DialogsChannel.SHOWN, form);
		channel.commands.result = CommandCheck.PARSE_ERRORS;

		dialogs.press("Submit");

		assertEquals(List.of("missing command"), channel.commands.checked);
		assertEquals(List.of(), channel.payloads);
		assertEquals(Optional.of(new CommandConfirmation("missing command", CommandCheck.PARSE_ERRORS)), dialogs.confirmation());
		assertEquals(Optional.empty(), dialogs.current());

		dialogs.decline();
		assertEquals(Optional.of(form), dialogs.current());

		dialogs.press("Submit");
		dialogs.confirm();
		assertEquals(List.of(DialogClick.command("missing command")), channel.payloads);
		assertEquals(Optional.empty(), dialogs.current());
		assertEquals(Optional.empty(), dialogs.confirmation());
	}

	@Test
	void needsCommandsOnlyForButtonsThatRunACommand() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, null);
		Dialog form = form(DialogAction.of(DialogActionType.RUN_COMMAND, "login secret"), DialogAfterAction.CLOSE);
		channel.publish(DialogsChannel.SHOWN, form);

		assertEquals("The button runs a command, which the client checks against its command tree first; install the Commands "
				+ "capability to press it", assertThrows(IllegalStateException.class, () -> dialogs.press("Submit")).getMessage());
		assertEquals(Optional.of(form), dialogs.current());

		channel.publish(DialogsChannel.SHOWN, form(DialogAction.custom(DialogActionType.CUSTOM, "example:ping", 7), DialogAfterAction.CLOSE));
		dialogs.press("Submit");
		assertEquals(List.of(DialogClick.custom(7, List.of())), channel.payloads);
	}

	@Test
	void neverSendsACommandWithASignedArgument() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		channel.publish(DialogsChannel.SHOWN, form(DialogAction.of(DialogActionType.RUN_COMMAND, "msg Bob hello"), DialogAfterAction.CLOSE));
		channel.commands.result = CommandCheck.SIGNATURE_REQUIRED;

		dialogs.press("Submit");
		assertEquals(CommandCheck.SIGNATURE_REQUIRED, dialogs.confirmation().orElseThrow().getReason());
		dialogs.confirm();

		assertEquals(List.of(), channel.payloads);
		assertEquals(Optional.empty(), dialogs.confirmation());
	}

	@Test
	void runsNoCommandsFromADialogShownDuringConfiguration() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		Dialog form = form(DialogAction.of(DialogActionType.RUN_COMMAND, "login secret"), DialogAfterAction.CLOSE).toBuilder()
				.configuration(true)
				.build();
		channel.publish(DialogsChannel.SHOWN, form);

		dialogs.press("Submit");

		assertEquals(List.of(), channel.commands.checked);
		assertEquals(List.of(), channel.payloads);
		assertEquals(Optional.of(form), dialogs.current());
	}

	@Test
	void opensANestedDialogInPlaceOfTheOpenOneAndReturnsBelowIt() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		Dialog rules = notice("Rules");
		Dialog form = form(DialogAction.show("", rules), DialogAfterAction.NONE);
		channel.publish(DialogsChannel.SHOWN, form);

		dialogs.press("Submit");

		assertEquals(Optional.of(rules), dialogs.current());
		assertEquals(List.of(form, rules), dialogs.history());
		dialogs.close();
		assertEquals(Optional.empty(), dialogs.current());
	}

	@Test
	void resolvesRegistryDialogsTheServerSentDuringConfiguration() {
		RecordingChannel channel = new RecordingChannel();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands);
		Dialog rules = notice("Rules").toBuilder().externalTitle("Server rules").build();
		channel.publish(DialogsChannel.REGISTRY, new DialogRegistry(List.of(
				new RegistryDialog("minecraft:server_links", null),
				new RegistryDialog("example:rules", rules)
		)));

		channel.publish(DialogsChannel.SHOWN, Dialog.builder().type("").registryId(1).title("").externalTitle("")
				.afterAction(DialogAfterAction.CLOSE).build());
		assertEquals(rules.toBuilder().registryId(1).registryName("example:rules").build(), dialogs.current().orElseThrow());

		channel.publish(DialogsChannel.SHOWN, Dialog.builder().type("").registryId(0).title("").externalTitle("")
				.afterAction(DialogAfterAction.CLOSE).build());
		assertEquals("minecraft:server_links", dialogs.current().orElseThrow().getRegistryName());

		channel.publish(DialogsChannel.SHOWN, Dialog.builder().type("minecraft:dialog_list").title("Menu").externalTitle("Menu")
				.button(new DialogButton("example:rules", DialogAction.show("example:rules", null), false))
				.closeWithEscape(true)
				.afterAction(DialogAfterAction.CLOSE)
				.build());
		assertEquals(List.of("Server rules"), dialogs.current().orElseThrow().getButtons().stream().map(DialogButton::getLabel).toList());
		dialogs.press("Server rules");
		assertEquals("example:rules", dialogs.current().orElseThrow().getRegistryName());
	}

	@Test
	void followsTheScreensTheClientShows() {
		RecordingChannel channel = new RecordingChannel();
		MutableClock clock = new MutableClock();
		ChannelDialogs dialogs = new ChannelDialogs(channel, channel.commands, clock);
		channel.publish(DialogsChannel.SHOWN, form(DialogAction.custom(DialogActionType.CUSTOM, "example:wait", 8), DialogAfterAction.WAIT_FOR_RESPONSE));

		dialogs.press("Submit");
		assertTrue(dialogs.waitingForResponse());
		assertEquals("The waiting screen allows going back 5 seconds after it appeared",
				assertThrows(IllegalStateException.class, dialogs::close).getMessage());
		clock.advance(ClientScreens.WAITING_BACK_DELAY);
		dialogs.close();
		assertFalse(dialogs.waitingForResponse());

		channel.publish(DialogsChannel.SHOWN, notice("First"));
		channel.publish(DialogsChannel.RESET, null);
		assertEquals(Optional.empty(), dialogs.current());
		assertEquals("Player has no screen open", assertThrows(IllegalStateException.class, dialogs::close).getMessage());
	}

	private static Dialog notice(String title) {
		return Dialog.builder()
				.type("minecraft:notice")
				.title(title)
				.externalTitle(title)
				.button(new DialogButton("gui.ok", null, true))
				.closeWithEscape(true)
				.afterAction(DialogAfterAction.CLOSE)
				.build();
	}

	private static Dialog form(DialogAction action, DialogAfterAction afterAction) {
		return Dialog.builder()
				.type("minecraft:multi_action")
				.title("Form")
				.externalTitle("Form")
				.input(DialogInput.builder().key("password").type(DialogInputType.TEXT).label("Password").initial("").maxLength(8).build())
				.input(DialogInput.builder().key("remember").type(DialogInputType.BOOLEAN).label("Remember").initial("false")
						.onTrue("yes").onFalse("no").build())
				.input(DialogInput.builder().key("amount").type(DialogInputType.NUMBER_RANGE).label("Amount").initial("5")
						.start(0F).end(10F).build())
				.input(DialogInput.builder().key("mode").type(DialogInputType.SINGLE_OPTION).label("Mode").initial("easy")
						.option(new DialogOption("easy", "Easy", true)).option(new DialogOption("hard", "Hard", false)).build())
				.button(new DialogButton("Submit", action, false))
				.closeWithEscape(false)
				.afterAction(afterAction)
				.build();
	}

	private static final class MutableClock extends Clock {
		private Instant now = Instant.EPOCH;

		private void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}

	/**
	 * Answers every command check with the result a test sets and records the checked commands.
	 */
	private static final class RecordingCommands implements Commands {
		private final List<String> checked = new ArrayList<>();
		private CommandCheck result = CommandCheck.NO_ISSUES;

		@Override
		public @NotNull Optional<CommandTree> tree() {
			return Optional.empty();
		}

		@Override
		public @NotNull CommandTree tree(@NotNull Predicate<CommandTree> matcher, @NotNull Duration timeout) {
			throw new AssertionError("Dialogs do not wait for command trees");
		}

		@Override
		public @NotNull CommandCheck check(@NotNull String command) {
			checked.add(command);
			return result;
		}
	}

	/**
	 * Records requests and delivers published events synchronously; a wait whose condition does not hold fails
	 * immediately instead of timing out.
	 */
	private static final class RecordingChannel implements ProtocolChannel {
		private final List<Object> payloads = new ArrayList<>();
		private final Map<String, List<Consumer<Object>>> listeners = new HashMap<>();
		private final RecordingCommands commands = new RecordingCommands();
		private Runnable onRequest = () -> { };


		private <E> void publish(EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			listeners.getOrDefault(eventDescriptor.getId(), List.of()).forEach(listener -> listener.accept(payload));
		}

		@Override
		public <Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
			assertEquals(DialogsChannel.CLICK.getId(), operation.getId());
			payloads.add(request);
			onRequest.run();
			return null;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> eventDescriptor, @NotNull Consumer<E> listener) {
			listeners.computeIfAbsent(eventDescriptor.getId(), ignored -> new ArrayList<>()).add((Consumer<Object>) listener);
			return () -> listeners.get(eventDescriptor.getId()).remove(listener);
		}

		@Override
		public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
			if (!condition.getAsBoolean()) throw new IllegalStateException("Player did not " + description);
		}

		@Override
		public @NotNull Set<String> installedCapabilities() {
			return Set.of(DialogsChannel.ID);
		}
	}
}
