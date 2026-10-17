package me.whereareiam.anvil.capability.dialogs;

import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import me.whereareiam.anvil.capability.dialogs.model.click.DialogValue;
import me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogButton;
import me.whereareiam.anvil.capability.dialogs.model.dialog.RegistryDialog;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPacketListener;
import me.whereareiam.anvil.capability.dialogs.packet.DialogsPackets;
import me.whereareiam.anvil.capability.dialogs.packet.RegistryDialogEntry;
import me.whereareiam.anvil.capability.dialogs.screen.DialogRegistry;
import me.whereareiam.anvil.capability.dialogs.type.DialogAfterAction;
import me.whereareiam.anvil.capability.dialogs.type.DialogInputType;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtString;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogsBindingTest {
	private static final Dialog LOGIN = Dialog.builder()
			.type("minecraft:notice")
			.title("Log in")
			.externalTitle("Log in")
			.closeWithEscape(true)
			.afterAction(DialogAfterAction.CLOSE)
			.button(new DialogButton("gui.ok", null, true))
			.build();
	private static final NbtCompound LOGIN_NBT = NbtCompound.builder()
			.entry("type", NbtString.of("minecraft:notice"))
			.entry("title", NbtString.of("Log in"))
			.build();

	@Test
	void sendsClicksThroughThePortWithThePlayersSession() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		player.startSession(new NativeSession());
		RecordingOperations operations = new RecordingOperations();
		new DialogsBinding<>(packets).bind(player, operations);

		assertNull(operations.execute(DialogsOperations.CLICK, DialogClick.command("login secret")));
		assertNull(operations.execute(DialogsOperations.CLICK, DialogClick.custom("example:submit", "{source:\"dialog\"}",
				List.of(new DialogValue("remember", DialogInputType.BOOLEAN, "true")))));

		assertEquals(List.of("dialogs.click"), List.copyOf(operations.handlers.keySet()));
		assertEquals(List.of("command login secret", "custom example:submit {source:\"dialog\",remember:1b}"), packets.sent);
		assertEquals(List.of(player.session, player.session), packets.sessions);
	}

	@Test
	void refusesAPayloadThatIsNotACompoundBeforeSending() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		player.startSession(new NativeSession());
		RecordingOperations operations = new RecordingOperations();
		new DialogsBinding<>(packets).bind(player, operations);

		assertThrows(IllegalArgumentException.class,
				() -> operations.execute(DialogsOperations.CLICK, DialogClick.custom("example:ping", "[1,2]", List.of())));

		assertEquals(List.of(), packets.sent);
	}

	@Test
	void sendsNothingWhileThePlayerIsDisconnected() {
		RecordingPackets packets = new RecordingPackets();
		RecordingOperations operations = new RecordingOperations();
		new DialogsBinding<>(packets).bind(new RecordingPlayer(), operations);

		IllegalStateException failure = assertThrows(IllegalStateException.class,
				() -> operations.execute(DialogsOperations.CLICK, DialogClick.command("login secret")));

		assertTrue(failure.getMessage().contains("not connected"), failure.getMessage());
		assertEquals(List.of(), packets.sent);
	}

	@Test
	void resetsTheScreensForEachNewSessionAndReportsOnlyTheCurrentSession() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		new DialogsBinding<>(packets).bind(player, new RecordingOperations());
		NativeSession first = new NativeSession();
		NativeSession second = new NativeSession();

		player.startSession(first);
		packets.listeners.get(first).shown(LOGIN_NBT, false);
		player.session = second;
		packets.listeners.get(first).cleared();
		player.startSession(second);
		packets.listeners.get(second).shown(LOGIN_NBT, true);
		packets.listeners.get(second).shownFromRegistry(1);
		packets.listeners.get(second).cleared();
		packets.listeners.get(second).reset();
		packets.listeners.get(second).registry(List.of(
				new RegistryDialogEntry("minecraft:server_links", null),
				new RegistryDialogEntry("example:rules", LOGIN_NBT)
		));

		assertEquals(List.of("dialogs.reset", "dialogs.shown", "dialogs.reset", "dialogs.shown", "dialogs.shown", "dialogs.cleared",
				"dialogs.reset", "dialogs.registry"), player.events);
		assertEquals(List.of(LOGIN, LOGIN.toBuilder().configuration(true).build(), Dialog.builder().type("").registryId(1).title("")
				.externalTitle("").closeWithEscape(true).afterAction(DialogAfterAction.CLOSE).build()), player.dialogs);
		assertEquals(new DialogRegistry(List.of(new RegistryDialog("minecraft:server_links", null), new RegistryDialog("example:rules", LOGIN))),
				assertInstanceOf(DialogRegistry.class, player.payloads.getLast()));
		assertEquals(List.of(second), List.copyOf(packets.listeners.keySet()));
	}

	@Test
	void removesTheListenerOfTheCurrentSessionWhenClosed() {
		RecordingPackets packets = new RecordingPackets();
		RecordingPlayer player = new RecordingPlayer();
		WorkerBinding binding = new DialogsBinding<>(packets).bind(player, new RecordingOperations());
		player.startSession(new NativeSession());

		binding.close();

		assertEquals(Map.of(), packets.listeners);
	}

	private static final class NativeSession {
	}

	private static final class RecordingPackets implements DialogsPackets<NativeSession> {
		private final List<String> sent = new ArrayList<>();
		private final List<NativeSession> sessions = new ArrayList<>();
		private final Map<NativeSession, DialogsPacketListener> listeners = new LinkedHashMap<>();

		@Override
		public @NotNull Class<NativeSession> sessionType() {
			return NativeSession.class;
		}

		@Override
		public void command(@NotNull NativeSession session, @NotNull String commandWithoutSlash) {
			sessions.add(session);
			sent.add("command " + commandWithoutSlash);
		}

		@Override
		public void customClick(@NotNull NativeSession session, @NotNull String id, @NotNull NbtCompound payload) {
			sessions.add(session);
			sent.add("custom " + id + " " + payload.toSnbt());
		}

		@Override
		public @NotNull Runnable listen(@NotNull NativeSession session, @NotNull DialogsPacketListener listener) {
			listeners.put(session, listener);
			return () -> listeners.remove(session);
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<Object, Object>> handlers = new LinkedHashMap<>();

		@Override
		@SuppressWarnings("unchecked")
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> channelOperation, @NotNull Function<Q, R> handler) {
			handlers.put(channelOperation.getId(), (Function<Object, Object>) handler);
		}

		private <Q> @Nullable Object execute(ChannelOperation<Q, ?> operation, Q request) {
			return handlers.get(operation.getId()).apply(request);
		}
	}

	/**
	 * A player whose native session generations the test starts: each {@link #startSession(Object)} closes the listeners
	 * of the previous generation and binds them for the new session, as the worker does before a session connects.
	 */
	private static final class RecordingPlayer implements PlayerBindingContext<Object> {
		private final List<String> events = new ArrayList<>();
		private final List<Dialog> dialogs = new ArrayList<>();
		private final List<Object> payloads = new ArrayList<>();
		private @Nullable Object session;
		private @Nullable Function<Object, Subscription> listener;
		private @Nullable Subscription generation;

		private void startSession(Object nativeSession) {
			if (generation != null) generation.close();
			generation = null;
			session = nativeSession;
			if (listener != null) generation = listener.apply(nativeSession);
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
			if (session == null) throw new IllegalStateException("Player Alice is not connected");

			return session;
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			return nativeSession == session;
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			this.listener = listener;
			return () -> {
				this.listener = null;
				if (generation != null) generation.close();
				generation = null;
			};
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
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
			events.add(eventDescriptor.getId());
			if (payload != null) payloads.add(payload);
			if (payload instanceof Dialog dialog) dialogs.add(dialog);
		}
	}
}
