package me.whereareiam.anvil.capability.dialogs;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.Expectation;
import me.whereareiam.anvil.capability.api.history.HistoryCursor;
import me.whereareiam.anvil.capability.commands.Commands;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import me.whereareiam.anvil.capability.dialogs.channel.DialogsChannel;
import me.whereareiam.anvil.capability.dialogs.model.DialogRegistry;
import me.whereareiam.anvil.capability.dialogs.model.click.CommandConfirmation;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.dialogs.screen.ClientScreens;
import me.whereareiam.anvil.capability.dialogs.screen.ClientScreens.ConfirmScreen;
import me.whereareiam.anvil.capability.dialogs.screen.ClientScreens.DialogScreen;
import me.whereareiam.anvil.capability.dialogs.screen.ClientScreens.Screen;
import me.whereareiam.anvil.capability.dialogs.screen.ClientScreens.WaitingScreen;
import me.whereareiam.anvil.capability.dialogs.screen.DialogPress;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import me.whereareiam.anvil.player.api.channel.ProtocolChannel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Dialogs capability driven over a player's typed channel: the events the worker reports and the buttons the test
 * presses move the player's {@link ClientScreens} as they move the client's, and every dialog shown is kept in the
 * history, across reconnects. Registry references are resolved through the last dialog registry the server sent,
 * and commands are checked through the player's {@link Commands}, if it has them. Clicks go to the worker's dialogs
 * extension. The channel releases the event subscriptions together with the player.
 */
final class ChannelDialogs implements Dialogs {
	private final ProtocolChannel channel;
	private final @Nullable Commands commands;
	private final CopyOnWriteArrayList<Dialog> history = new CopyOnWriteArrayList<>();
	private final ClientScreens screens;
	private volatile DialogRegistry registry = new DialogRegistry(List.of());

	ChannelDialogs(@NotNull ProtocolChannel channel, @Nullable Commands commands) {
		this(channel, commands, Clock.systemUTC());
	}

	ChannelDialogs(@NotNull ProtocolChannel channel, @Nullable Commands commands, @NotNull Clock clock) {
		this.channel = channel;
		this.commands = commands;
		this.screens = new ClientScreens(clock);
		channel.subscribe(DialogsChannel.REGISTRY, update -> registry = update);
		channel.subscribe(DialogsChannel.SHOWN, dialog -> {
			synchronized (screens) {
				open(dialog, screens.screen());
			}
		});
		channel.subscribe(DialogsChannel.CLEARED, ignored -> {
			synchronized (screens) {
				screens.clear();
			}
		});
		channel.subscribe(DialogsChannel.RESET, ignored -> {
			synchronized (screens) {
				screens.reset();
			}
		});
	}

	@Override
	public @NotNull Optional<Dialog> current() {
		synchronized (screens) {
			return Optional.ofNullable(screens.dialog());
		}
	}

	@Override
	public @NotNull Optional<CommandConfirmation> confirmation() {
		synchronized (screens) {
			return screens.screen() instanceof ConfirmScreen confirm ? Optional.of(confirm.confirmation()) : Optional.empty();
		}
	}

	@Override
	public boolean waitingForResponse() {
		synchronized (screens) {
			return screens.screen() instanceof WaitingScreen;
		}
	}

	@Override
	public @NotNull List<Dialog> history() {
		return List.copyOf(history);
	}

	@Override
	public @NotNull Dialog shown(@NotNull String title, @NotNull Duration timeout) {
		channel.await(() -> openTitled(title) != null, "show a dialog titled '" + title + "'", timeout);
		return Objects.requireNonNull(openTitled(title));
	}

	@Override
	public @NotNull DialogWatch watch() {
		return new Watch(new HistoryCursor<>(this::history));
	}

	@Override
	public void closed(@NotNull Duration timeout) {
		channel.await(() -> current().isEmpty(), "close its dialog", timeout);
	}

	@Override
	public void press(@NotNull String button, @NotNull Map<String, String> inputs) {
		DialogScreen open;
		DialogButton pressed;
		Screen next;
		synchronized (screens) {
			open = openDialog();
			Dialog dialog = open.dialog();
			pressed = dialog.buttons().stream()
					.filter(candidate -> candidate.label().equals(button))
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("The open dialog '" + dialog.title() + "' has no button '" + button
							+ "'; buttons: " + dialog.buttons().stream().map(DialogButton::label).toList()));
			next = screens.after(open, dialog.afterAction());
		}
		run(open, pressed.action(), inputs, next);
	}

	@Override
	public void close() {
		DialogScreen open;
		synchronized (screens) {
			switch (screens.screen()) {
				case DialogScreen dialog -> open = dialog;
				case WaitingScreen waiting -> {
					if (!screens.canLeave(waiting))
						throw new IllegalStateException("The waiting screen allows going back "
								+ ClientScreens.WAITING_BACK_DELAY.toSeconds() + " seconds after it appeared");
					screens.set(waiting.previous());
					return;
				}
				case ConfirmScreen confirm -> {
					screens.set(confirm.declined());
					return;
				}
				case null -> throw new IllegalStateException("Player has no screen open");
			}
			if (!open.dialog().closeWithEscape())
				throw new IllegalStateException("The open dialog '" + open.dialog().title() + "' cannot be closed with Escape");
		}

		DialogAction exit = open.dialog().buttons().stream()
				.filter(DialogButton::exit)
				.findFirst()
				.map(DialogButton::action)
				.orElse(null);
		run(open, exit, Map.of(), open.previous());
	}

	@Override
	public void confirm() {
		CommandConfirmation confirmation;
		synchronized (screens) {
			ConfirmScreen confirm = openConfirmation();
			confirmation = confirm.confirmation();
			screens.set(confirm.confirmed());
		}
		if (confirmation.reason() != CommandCheck.SIGNATURE_REQUIRED)
			channel.request(DialogsChannel.CLICK, DialogClick.command(confirmation.command()));
	}

	@Override
	public void decline() {
		synchronized (screens) {
			screens.set(openConfirmation().declined());
		}
	}

	/**
	 * Runs a button's action from the open dialog as the client's dialog screen does. The client shows the screen
	 * the button leads to before the server can answer what it sends, so the screens change before anything is sent;
	 * requests to the worker run outside the lock that events take.
	 */
	private void run(DialogScreen open, @Nullable DialogAction action, Map<String, String> inputs, @Nullable Screen next) {
		DialogClick click = DialogPress.click(action, open.dialog().inputs(), inputs);
		if (action != null && action.type() == DialogActionType.SHOW_DIALOG) {
			Dialog nested = nested(action).toBuilder().configuration(open.dialog().configuration()).build();
			synchronized (screens) {
				still(open);
				open(nested, next);
			}
			return;
		}
		if (click == null || click.command() == null) {
			synchronized (screens) {
				still(open);
				screens.set(next);
			}
			if (click != null) channel.request(DialogsChannel.CLICK, click);
			return;
		}

		// The client runs no commands during configuration and leaves the dialog as it is.
		if (open.dialog().configuration()) return;
		if (commands == null)
			throw new IllegalStateException("The button runs a command, which the client checks against its command tree first; "
					+ "install the Commands capability to press it");

		CommandCheck check = commands.check(click.command());
		synchronized (screens) {
			still(open);
			if (check != CommandCheck.NO_ISSUES) {
				screens.confirm(click.command(), check, next);
				return;
			}

			screens.set(next);
		}
		channel.request(DialogsChannel.CLICK, click);
	}

	private void still(DialogScreen open) {
		if (screens.screen() != open) throw new IllegalStateException("The server replaced the dialog '" + open.dialog().title()
				+ "' while its button was pressed");
	}

	private Dialog nested(DialogAction action) {
		if (action.dialog() != null) return action.dialog();

		Dialog found = registry.find(action.value());
		if (found == null)
			throw new IllegalArgumentException("The button opens the registry dialog " + action.value()
					+ ", which the server did not send");
		return found;
	}

	/**
	 * Shows a dialog from a screen, resolving registry references first, and records it.
	 */
	private void open(Dialog dialog, @Nullable Screen from) {
		Dialog resolved = resolve(dialog);
		history.add(resolved);
		screens.show(resolved, from);
	}

	/**
	 * Resolves a dialog the server referred to by registry ID, and the registry dialogs its buttons open, whose
	 * dialog-list buttons are labelled with the dialog's external title.
	 */
	private Dialog resolve(Dialog dialog) {
		DialogRegistry known = registry;
		Dialog resolved = dialog.registryId() == null || dialog.registryName() != null ? dialog : known.resolve(dialog);
		if (resolved.buttons().stream().noneMatch(button -> unresolved(button.action()))) return resolved;

		Dialog.DialogBuilder builder = resolved.toBuilder().clearButtons();
		for (DialogButton button : resolved.buttons()) {
			DialogAction action = button.action();
			Dialog target = unresolved(action) ? known.find(action.value()) : null;
			if (target == null) {
				builder.button(button);
				continue;
			}

			String label = button.label().equals(action.value()) ? target.externalTitle() : button.label();
			builder.button(new DialogButton(label, DialogAction.show(action.value(), target), button.exit()));
		}
		return builder.build();
	}

	private static boolean unresolved(@Nullable DialogAction action) {
		return action != null
				&& action.type() == DialogActionType.SHOW_DIALOG
				&& action.dialog() == null
				&& !action.value().isEmpty();
	}

	private DialogScreen openDialog() {
		if (screens.screen() instanceof DialogScreen open) return open;
		throw new IllegalStateException("Player has no dialog open");
	}

	private ConfirmScreen openConfirmation() {
		if (screens.screen() instanceof ConfirmScreen confirm) return confirm;
		throw new IllegalStateException("Player has no command confirmation open");
	}

	private @Nullable Dialog openTitled(String title) {
		Dialog dialog = current().orElse(null);
		return dialog != null && dialog.title().contains(title) ? dialog : null;
	}

	/**
	 * A watch over this player's dialog history, which waits through the player's channel.
	 */
	@RequiredArgsConstructor
	private final class Watch implements DialogWatch {
		private final HistoryCursor<Dialog> cursor;

		@Override
		public @NotNull Dialog shown(@NotNull Predicate<Dialog> matcher, @NotNull Duration timeout) {
			channel.await(() -> cursor.find(matcher) != null, "show a matching dialog", timeout);
			return Objects.requireNonNull(cursor.take(matcher));
		}

		@Override
		public void neverShown(@NotNull Predicate<Dialog> matcher, @NotNull Duration duration) {
			try {
				channel.await(() -> cursor.find(matcher) != null, "show a matching dialog", duration);
			} catch (IllegalStateException quiet) {
				if (cursor.find(matcher) == null) return;
			}

			throw new IllegalStateException("Player was shown a dialog it must not be shown: "
					+ Objects.requireNonNull(cursor.find(matcher)).title());
		}

		@Override
		public @NotNull Expectation expectShown(@NotNull Predicate<Dialog> matcher) {
			return Expectation.of("show a matching dialog", () -> cursor.find(matcher) != null);
		}

		@Override
		public @NotNull List<Dialog> unread() {
			return cursor.unread();
		}
	}
}
