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
import me.whereareiam.anvil.protocol.java.api.model.configuration.RegistryEntry;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtByte;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtFloat;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtInt;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtList;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtTag;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ServerboundChatCommand;
import me.whereareiam.anvil.protocol.java.api.packet.configuration.ClientboundRegistryData;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.packet.dialog.ClientboundClearDialog;
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
import java.util.Objects;
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
	private static final NbtCompound NOTICE = NbtCompound.builder()
			.put("type", new NbtString("minecraft:notice"))
			.put("title", new NbtString("Log in"))
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
	void sendsCommandsAsUnsignedCommandsAndCustomClicksWithTheDeclaredIdAndPayload() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);
		protocol.deliver(new ClientboundShowDialog(null, actions(
				custom("minecraft:custom", "example:leave", "payload", NbtCompound.EMPTY.with("source", new NbtString("dialog"))),
				custom("minecraft:custom", "example:ping", "payload", null)
		), false));

		assertNull(operations.execute(DialogsChannel.CLICK, DialogClick.command("login secret")));
		assertNull(operations.execute(DialogsChannel.CLICK, DialogClick.custom(handle(player, 0), List.of())));
		assertNull(operations.execute(DialogsChannel.CLICK, DialogClick.custom(handle(player, 1), List.of())));

		assertEquals(List.of("dialogs.click"), List.copyOf(operations.handlers.keySet()));
		assertEquals(List.of(
				new ServerboundChatCommand("login secret"),
				new ServerboundCustomClickAction("example:leave", NbtCompound.EMPTY.with("source", new NbtString("dialog"))),
				new ServerboundCustomClickAction("example:ping", NbtCompound.EMPTY)
		), protocol.sent);
	}

	@Test
	void sendsADynamicCustomClickWithTheValuesPutIntoTheDeclaredAdditions() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);
		protocol.deliver(new ClientboundShowDialog(null, actions(custom("minecraft:dynamic/custom", "example:submit", "additions",
				NbtCompound.builder().put("source", new NbtString("dialog")).put("password", new NbtString("old")).build())), false));

		operations.execute(DialogsChannel.CLICK, DialogClick.custom(handle(player, 0), List.of(
				new DialogValue("password", DialogInputType.TEXT, "secret"),
				new DialogValue("remember", DialogInputType.BOOLEAN, "true"),
				new DialogValue("hidden", DialogInputType.BOOLEAN, "false"),
				new DialogValue("amount", DialogInputType.NUMBER_RANGE, "2.5"),
				new DialogValue("mode", DialogInputType.SINGLE_OPTION, "hard")
		)));

		assertEquals(List.of(new ServerboundCustomClickAction("example:submit", NbtCompound.builder()
				.put("source", new NbtString("dialog"))
				.put("password", new NbtString("secret"))
				.put("remember", NbtByte.of(true))
				.put("hidden", NbtByte.of(false))
				.put("amount", new NbtFloat(2.5F))
				.put("mode", new NbtString("hard"))
				.build())), protocol.sent);
	}

	@Test
	void refusesACustomClickWhoseDeclaredPayloadIsNotACompound() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);
		protocol.deliver(new ClientboundShowDialog(null, actions(custom("minecraft:custom", "example:ping", "payload",
				new NbtList(List.of(new NbtInt(1), new NbtInt(2))))), false));

		assertThrows(IllegalArgumentException.class,
				() -> operations.execute(DialogsChannel.CLICK, DialogClick.custom(handle(player, 0), List.of())));
		assertEquals(List.of(), protocol.sent);
	}

	@Test
	void refusesAClickNamingAnActionOfADialogThatIsGone() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);
		List<ClientboundPacket> endings = List.of(
				new ClientboundShowDialog(null, NOTICE, false),
				new ClientboundClearDialog(),
				new ClientboundLogin(42),
				new ClientboundRespawn(),
				new ClientboundStartConfiguration()
		);

		for (ClientboundPacket ending : endings) {
			protocol.deliver(new ClientboundShowDialog(null, actions(custom("minecraft:custom", "example:ping", "payload", null)), false));
			long handle = handle(player, 0);
			protocol.deliver(ending);

			IllegalStateException failure = assertThrows(IllegalStateException.class,
					() -> operations.execute(DialogsChannel.CLICK, DialogClick.custom(handle, List.of())));
			assertTrue(failure.getMessage().contains("no longer shown"), failure.getMessage());
		}

		protocol.deliver(new ClientboundShowDialog(null, actions(custom("minecraft:custom", "example:ping", "payload", null)), false));
		long handle = handle(player, 0);
		player.startConnection();

		assertThrows(IllegalStateException.class, () -> operations.execute(DialogsChannel.CLICK, DialogClick.custom(handle, List.of())));
		assertEquals(List.of(), protocol.sent);
	}

	@Test
	void keepsTheActionsOfTheDialogRegistryUntilANewRegistryReplacesIt() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);
		NbtCompound rules = actions(custom("minecraft:custom", "example:agree", "payload", null));

		protocol.deliver(new ClientboundRegistryData("minecraft:dialog", List.of(new RegistryEntry("example:rules", rules))));
		DialogRegistry registry = (DialogRegistry) player.payloads.getLast();
		long handle = registry.getDialogs().getFirst().getDialog().getButtons().getFirst().getAction().getHandle();
		protocol.deliver(new ClientboundShowDialog(0, null, false));
		protocol.deliver(new ClientboundClearDialog());
		operations.execute(DialogsChannel.CLICK, DialogClick.custom(handle, List.of()));
		protocol.deliver(new ClientboundRegistryData("minecraft:dialog", List.of()));

		assertEquals(List.of(new ServerboundCustomClickAction("example:agree", NbtCompound.EMPTY)), protocol.sent);
		assertThrows(IllegalStateException.class, () -> operations.execute(DialogsChannel.CLICK, DialogClick.custom(handle, List.of())));
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

		protocol.deliver(new ClientboundShowDialog(null, NbtCompound.builder()
				.put("type", new NbtString("minecraft:multi_action"))
				.put("title", new NbtString("Identica"))
				.put("external_title", new NbtString("Log in"))
				.put("body", NbtCompound.builder()
						.put("type", new NbtString("minecraft:plain_message"))
						.put("contents", new NbtString("Welcome back"))
						.build())
				.put("inputs", new NbtList(List.of(
						NbtCompound.builder()
								.put("type", new NbtString("minecraft:text"))
								.put("key", new NbtString("password"))
								.put("label", new NbtString("Password"))
								.put("max_length", new NbtInt(64))
								.build(),
						NbtCompound.builder()
								.put("type", new NbtString("minecraft:boolean"))
								.put("key", new NbtString("remember"))
								.put("label", new NbtString("Remember"))
								.put("initial", NbtByte.of(true))
								.put("on_true", new NbtString("yes"))
								.build(),
						NbtCompound.builder()
								.put("type", new NbtString("minecraft:single_option"))
								.put("key", new NbtString("mode"))
								.put("label", new NbtString("Mode"))
								.put("options", new NbtList(List.of(NbtCompound.builder()
										.put("id", new NbtString("hard"))
										.put("display", new NbtString("Hard"))
										.put("initial", NbtByte.of(true))
										.build())))
								.build(),
						NbtCompound.builder()
								.put("type", new NbtString("minecraft:number_range"))
								.put("key", new NbtString("amount"))
								.put("label", new NbtString("Amount"))
								.put("start", new NbtFloat(1F))
								.put("end", new NbtFloat(4F))
								.put("step", new NbtFloat(0.5F))
								.build(),
						NbtCompound.builder()
								.put("type", new NbtString("example:colour"))
								.put("key", new NbtString("colour"))
								.build()
				)))
				.put("actions", new NbtList(List.of(
						button("Help", NbtCompound.builder()
								.put("type", new NbtString("minecraft:dynamic/custom"))
								.put("id", new NbtString("example:help"))
								.put("additions", NbtCompound.EMPTY.with("page", new NbtInt(2)))
								.build()),
						button("Rules", NbtCompound.builder()
								.put("type", new NbtString("minecraft:show_dialog"))
								.put("dialog", NOTICE)
								.build())
				)))
				.put("exit_action", button("Leave", NbtCompound.builder()
						.put("type", new NbtString("minecraft:run_command"))
						.put("command", new NbtString("/hub"))
						.build()))
				.put("can_close_with_escape", NbtByte.of(false))
				.put("after_action", new NbtString("wait_for_response"))
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
				.button(new DialogButton("Help", DialogAction.custom(DialogActionType.DYNAMIC_CUSTOM, "example:help", 1), false))
				.button(new DialogButton("Rules", DialogAction.show("", LOGIN), false))
				.button(new DialogButton("Leave", DialogAction.of(DialogActionType.RUN_COMMAND, "/hub"), true))
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
	void reportsOnlyTheDialogRegistryInItsOrder() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundRegistryData("minecraft:dimension_type", List.of(
				new RegistryEntry("minecraft:overworld", NbtCompound.EMPTY.with("height", new NbtInt(384)))
		)));
		protocol.deliver(new ClientboundRegistryData("minecraft:dialog", List.of(
				new RegistryEntry("minecraft:server_links", null),
				new RegistryEntry("example:rules", NOTICE)
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
	 * A multi-action dialog with one button per action, labelled by its position.
	 */
	private static NbtCompound actions(NbtCompound... actions) {
		List<NbtCompound> buttons = new ArrayList<>();
		for (NbtCompound action : actions) buttons.add(button(Integer.toString(buttons.size()), action));

		return NbtCompound.builder()
				.put("type", new NbtString("minecraft:multi_action"))
				.put("title", new NbtString("Actions"))
				.put("actions", new NbtList(buttons))
				.build();
	}

	private static NbtCompound custom(String type, String id, String payloadField, @Nullable NbtTag payload) {
		NbtCompound action = NbtCompound.builder()
				.put("type", new NbtString(type))
				.put("id", new NbtString(id))
				.build();
		return payload == null ? action : action.with(payloadField, payload);
	}

	/**
	 * Returns the handle of a button's action in the dialog the player was shown last.
	 */
	private static long handle(RecordingPlayer player, int button) {
		Dialog shown = (Dialog) player.payloads.getLast();
		return Objects.requireNonNull(shown.getButtons().get(button).getAction().getHandle());
	}

	private static NbtCompound button(String label, NbtCompound action) {
		return NbtCompound.builder()
				.put("label", new NbtString(label))
				.put("action", action)
				.build();
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
