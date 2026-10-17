package me.whereareiam.anvil.capability.dialogs.java;

import me.whereareiam.anvil.capability.dialogs.channel.DialogsChannel;
import me.whereareiam.anvil.capability.dialogs.model.DialogRegistry;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogInput;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogOption;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import me.whereareiam.anvil.capability.dialogs.type.DialogActionType;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInputValue;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundClearDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundShowDialog;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ServerboundCustomClickAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaDialogsExtensionTest {
	private static final me.whereareiam.anvil.protocol.java.api.model.dialog.Dialog NOTICE =
			me.whereareiam.anvil.protocol.java.api.model.dialog.Dialog.builder()
					.type("minecraft:notice")
					.title("Log in")
					.externalTitle("Log in")
					.button(new me.whereareiam.anvil.protocol.java.api.model.dialog.DialogButton("gui.ok", null, true))
					.closeWithEscape(true)
					.afterAction(me.whereareiam.anvil.protocol.java.api.type.DialogAfterAction.CLOSE)
					.build();
	private static final Dialog LOGIN = Dialog.builder()
			.type("minecraft:notice")
			.title("Log in")
			.externalTitle("Log in")
			.button(new DialogButton("gui.ok", null, true))
			.closeWithEscape(true)
			.afterAction(DialogAfterAction.CLOSE)
			.build();

	private final JavaDialogsExtension extension = new JavaDialogsExtension();

	@Test
	void isDiscoveredAsTheEditionWideDialogsExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaDialogsExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.dialogs", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void sendsCommandsAsUnsignedCommandsAndCustomClicksWithTheirPayload() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingOperations operations = new RecordingOperations();
		extension.bind(new RecordingPlayer(protocol), operations);

		assertNull(operations.execute(DialogsChannel.CLICK, DialogClick.command("login secret")));
		assertNull(operations.execute(DialogsChannel.CLICK, DialogClick.custom("example:leave", "{source:\"dialog\"}", List.of())));
		assertNull(operations.execute(DialogsChannel.CLICK, DialogClick.custom("example:ping", null, List.of())));

		assertEquals(List.of("dialogs.click"), List.copyOf(operations.handlers.keySet()));
		assertEquals(List.of(
				new ServerboundChatCommand("login secret"),
				new ServerboundCustomClickAction("example:leave", "{source:\"dialog\"}", List.of()),
				new ServerboundCustomClickAction("example:ping", null, List.of())
		), protocol.sent);
	}

	@Test
	void sendsTheValuesOfADynamicCustomClickTypedWithTheDeclaredPayloadUnchanged() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingOperations operations = new RecordingOperations();
		extension.bind(new RecordingPlayer(protocol), operations);

		operations.execute(DialogsChannel.CLICK, DialogClick.custom("example:submit", "{source:\"dialog\",password:\"old\"}", List.of(
				new DialogValue("password", DialogInputType.TEXT, "secret"),
				new DialogValue("remember", DialogInputType.BOOLEAN, "true"),
				new DialogValue("hidden", DialogInputType.BOOLEAN, "false"),
				new DialogValue("amount", DialogInputType.NUMBER_RANGE, "2.5"),
				new DialogValue("mode", DialogInputType.SINGLE_OPTION, "hard")
		)));

		assertEquals(List.of(new ServerboundCustomClickAction("example:submit", "{source:\"dialog\",password:\"old\"}", List.of(
				DialogInputValue.text("password", "secret"),
				DialogInputValue.checkbox("remember", true),
				DialogInputValue.checkbox("hidden", false),
				DialogInputValue.slider("amount", 2.5F),
				DialogInputValue.text("mode", "hard")
		))), protocol.sent);
	}

	@Test
	void sendsNothingWhileThePlayerIsDisconnected() {
		RecordingProtocol protocol = new RecordingProtocol();
		protocol.connected = false;
		RecordingOperations operations = new RecordingOperations();
		extension.bind(new RecordingPlayer(protocol), operations);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> operations.execute(DialogsChannel.CLICK, DialogClick.command("login secret")));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertEquals(List.of(), protocol.sent);
	}

	@Test
	void resetsTheScreensForEachNewConnectionAndWheneverTheClientReplacesThem() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		player.startConnection();
		protocol.deliver(new ClientboundShowDialog(null, NOTICE, false));
		protocol.deliver(new ClientboundClearDialog());
		protocol.deliver(new ClientboundLogin(42));
		protocol.deliver(new ClientboundRespawn());
		protocol.deliver(new ClientboundStartConfiguration());
		player.startConnection();

		assertEquals(List.of("dialogs.reset", "dialogs.shown", "dialogs.cleared", "dialogs.reset", "dialogs.reset", "dialogs.reset",
				"dialogs.reset"), player.events);
		assertEquals(LOGIN, player.payloads.get(1));
	}

	@Test
	void mapsShownDialogsWithTheirInputsButtonsAndNestedDialogs() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundShowDialog(null, me.whereareiam.anvil.protocol.java.api.model.dialog.Dialog.builder()
				.type("minecraft:multi_action")
				.title("Identica")
				.externalTitle("Log in")
				.body("Welcome back")
				.input(me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInput.builder()
						.key("password")
						.type(me.whereareiam.anvil.protocol.java.api.type.DialogInputType.TEXT)
						.label("Password")
						.initial("")
						.maxLength(64)
						.build())
				.input(me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInput.builder()
						.key("remember")
						.type(me.whereareiam.anvil.protocol.java.api.type.DialogInputType.BOOLEAN)
						.label("Remember")
						.initial("true")
						.onTrue("yes")
						.onFalse("false")
						.build())
				.input(me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInput.builder()
						.key("mode")
						.type(me.whereareiam.anvil.protocol.java.api.type.DialogInputType.SINGLE_OPTION)
						.label("Mode")
						.initial("hard")
						.option(new me.whereareiam.anvil.protocol.java.api.model.dialog.DialogOption("hard", "Hard", true))
						.build())
				.input(me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInput.builder()
						.key("amount")
						.type(me.whereareiam.anvil.protocol.java.api.type.DialogInputType.NUMBER_RANGE)
						.label("Amount")
						.initial("2.5")
						.start(1F)
						.end(4F)
						.step(0.5F)
						.build())
				.input(me.whereareiam.anvil.protocol.java.api.model.dialog.DialogInput.builder()
						.key("colour")
						.type(me.whereareiam.anvil.protocol.java.api.type.DialogInputType.OTHER)
						.label("")
						.initial("")
						.build())
				.button(new me.whereareiam.anvil.protocol.java.api.model.dialog.DialogButton("Help",
						new me.whereareiam.anvil.protocol.java.api.model.dialog.DialogAction(
								me.whereareiam.anvil.protocol.java.api.type.DialogActionType.DYNAMIC_CUSTOM, "example:help",
								"{page:2}", null), false))
				.button(new me.whereareiam.anvil.protocol.java.api.model.dialog.DialogButton("Rules",
						new me.whereareiam.anvil.protocol.java.api.model.dialog.DialogAction(
								me.whereareiam.anvil.protocol.java.api.type.DialogActionType.SHOW_DIALOG, "", null, NOTICE), false))
				.button(new me.whereareiam.anvil.protocol.java.api.model.dialog.DialogButton("Leave",
						new me.whereareiam.anvil.protocol.java.api.model.dialog.DialogAction(
								me.whereareiam.anvil.protocol.java.api.type.DialogActionType.RUN_COMMAND, "/hub", null, null), true))
				.closeWithEscape(false)
				.afterAction(me.whereareiam.anvil.protocol.java.api.type.DialogAfterAction.WAIT_FOR_RESPONSE)
				.build(), false));

		assertEquals(Dialog.builder()
				.type("minecraft:multi_action")
				.title("Identica")
				.externalTitle("Log in")
				.body("Welcome back")
				.input(DialogInput.builder().key("password").type(DialogInputType.TEXT).label("Password").initial("").maxLength(64).build())
				.input(DialogInput.builder().key("remember").type(DialogInputType.BOOLEAN).label("Remember").initial("true")
						.onTrue("yes").onFalse("false").build())
				.input(DialogInput.builder().key("mode").type(DialogInputType.SINGLE_OPTION).label("Mode").initial("hard")
						.option(new DialogOption("hard", "Hard", true)).build())
				.input(DialogInput.builder().key("amount").type(DialogInputType.NUMBER_RANGE).label("Amount").initial("2.5")
						.start(1F).end(4F).step(0.5F).build())
				.input(DialogInput.builder().key("colour").type(DialogInputType.OTHER).label("").initial("").build())
				.button(new DialogButton("Help", DialogAction.of(DialogActionType.DYNAMIC_CUSTOM, "example:help", "{page:2}"), false))
				.button(new DialogButton("Rules", DialogAction.show("", LOGIN), false))
				.button(new DialogButton("Leave", DialogAction.of(DialogActionType.RUN_COMMAND, "/hub", null), true))
				.closeWithEscape(false)
				.afterAction(DialogAfterAction.WAIT_FOR_RESPONSE)
				.build(), player.payloads.getLast());
	}

	@Test
	void reportsRegistryReferencesAndConfigurationDialogs() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundShowDialog(3, null, false));
		protocol.deliver(new ClientboundShowDialog(null, NOTICE, true));

		assertEquals(Dialog.builder()
				.type("")
				.registryId(3)
				.title("")
				.externalTitle("")
				.closeWithEscape(true)
				.afterAction(DialogAfterAction.CLOSE)
				.build(), player.payloads.get(0));
		assertEquals(LOGIN.toBuilder().configuration(true).build(), player.payloads.get(1));
	}

	@Test
	void reportsTheDialogRegistryInItsOrder() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundRegistryData(List.of(
				new me.whereareiam.anvil.protocol.java.api.model.dialog.RegistryDialog("minecraft:server_links", null),
				new me.whereareiam.anvil.protocol.java.api.model.dialog.RegistryDialog("example:rules", NOTICE)
		)));

		assertEquals(List.of("dialogs.registry"), player.events);
		assertEquals(new DialogRegistry(List.of(
				new RegistryDialog("minecraft:server_links", null),
				new RegistryDialog("example:rules", LOGIN)
		)), player.payloads.getFirst());
	}

	@Test
	void stopsListeningAndObservingConnectionsWhenClosed() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		WorkerBinding binding = extension.bind(player, new RecordingOperations());

		binding.close();
		player.startConnection();
		protocol.deliver(new ClientboundClearDialog());

		assertEquals(Map.of(), protocol.listeners);
		assertNull(player.connections);
		assertEquals(List.of(), player.events);
	}

	@Test
	void isUnavailableForAReleaseWithoutDialogs() {
		RecordingProtocol protocol = new RecordingProtocol();
		protocol.unsupported.add(ClientboundShowDialog.class);
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();

		ProtocolUnavailableException failure = assertThrows(ProtocolUnavailableException.class,
				() -> extension.bind(player, operations));

		assertTrue(failure.getMessage().contains(ClientboundShowDialog.class.getName()), failure.getMessage());
		assertEquals(Map.of(), operations.handlers);
		assertEquals(Map.of(), protocol.listeners);
		assertNull(player.connections);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingPlayer player = new RecordingPlayer(null);

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(player, new RecordingOperations()));
		assertNull(player.connections);
	}

	/**
	 * Hands delivered packets to the listener of their class and records the packets sent while connected.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();
		private final List<ServerboundPacket> sent = new ArrayList<>();
		private final Set<Class<?>> unsupported = new HashSet<>();
		private boolean connected = true;

		@Override
		public <P extends ClientboundPacket> @NotNull Subscription listen(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			listeners.put(type, packet -> listener.accept(type.cast(packet)));
			return () -> listeners.remove(type);
		}

		@Override
		public void send(@NotNull ServerboundPacket packet) {
			if (!connected) throw new IllegalStateException("Player Alice is not connected");

			sent.add(packet);
		}

		@Override
		public <P extends ServerboundPacket> @NotNull Subscription listenSent(
				@NotNull Class<P> type,
				@NotNull Consumer<? super P> listener
		) {
			throw new AssertionError("The dialogs binding observes no sent packets");
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return !unsupported.contains(type);
		}

		private void deliver(ClientboundPacket packet) {
			Consumer<Object> listener = listeners.get(packet.getClass());
			if (listener != null) listener.accept(packet);
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), (Function<Object, Object>) handler);
		}

		private <Q> @Nullable Object execute(OperationDescriptor<Q, ?> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}

	/**
	 * A player that speaks the given protocol, or none, whose connections the test starts: each
	 * {@link #startConnection()} runs the connection listener as the worker does before a new session connects.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final @Nullable JavaProtocol protocol;
		private final List<String> events = new ArrayList<>();
		private final List<Object> payloads = new ArrayList<>();
		private @Nullable Function<Object, Subscription> connections;

		private RecordingPlayer(@Nullable JavaProtocol protocol) {
			this.protocol = protocol;
		}

		private void startConnection() {
			if (connections != null) connections.apply(new Object());
		}

		@Override
		public <P> @NotNull P protocol(@NotNull Class<P> type) {
			if (protocol == null) return PlayerBindingContext.super.protocol(type);

			return type.cast(protocol);
		}

		@Override
		public @NotNull String name() {
			return "Alice";
		}

		@Override
		public @NotNull UUID uniqueId() {
			return UUID.nameUUIDFromBytes("Alice".getBytes());
		}

		@Override
		public void connect() {
			throw new AssertionError("Dialogs do not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("Dialogs do not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("Dialogs do not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("Dialogs use the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("Dialogs use the protocol, not the native session");
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			connections = listener;
			return () -> connections = null;
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("Dialogs do not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("Dialogs do not change the view rotation");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			events.add(event.getId());
			payloads.add(payload);
		}
	}
}
