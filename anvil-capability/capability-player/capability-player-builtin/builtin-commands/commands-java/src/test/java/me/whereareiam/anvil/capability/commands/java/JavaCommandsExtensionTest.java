package me.whereareiam.anvil.capability.commands.java;

import me.whereareiam.anvil.capability.commands.model.CommandTree;
import me.whereareiam.anvil.capability.commands.model.CommandTreeNode;
import me.whereareiam.anvil.capability.commands.type.CommandNodeType;
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
import me.whereareiam.anvil.protocol.java.api.model.command.CommandNode;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.command.ClientboundCommands;
import me.whereareiam.anvil.protocol.java.api.type.StringArgumentMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaCommandsExtensionTest {
	private static final ClientboundCommands EMPTY = new ClientboundCommands(List.of(), 0);

	private final JavaCommandsExtension extension = new JavaCommandsExtension();

	@Test
	void isDiscoveredAsTheEditionWideCommandsExtension() {
		WorkerExtension<?> discovered = ServiceLoader.load(WorkerExtension.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(JavaCommandsExtension.class::isInstance)
				.findFirst()
				.orElseThrow();

		assertEquals("me.whereareiam.anvil.commands", discovered.id());
		assertEquals(Optional.empty(), discovered.libraryId());
		assertEquals(Object.class, discovered.nativeSessionType());
	}

	@Test
	void resetsForEachNewConnectionAndReportsItsTreesWithoutRegisteringOperations() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		RecordingOperations operations = new RecordingOperations();
		extension.bind(player, operations);

		player.startConnection();
		protocol.deliver(EMPTY);
		player.startConnection();
		protocol.deliver(EMPTY);

		assertEquals(List.of("commands.reset", "commands.tree", "commands.reset", "commands.tree"), player.events);
		assertEquals(Map.of(), operations.handlers);
	}

	@Test
	void mapsTheDeclaredNodesToTheCommandTree() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		extension.bind(player, new RecordingOperations());

		protocol.deliver(new ClientboundCommands(List.of(
				CommandNode.builder().type(me.whereareiam.anvil.protocol.java.api.type.CommandNodeType.ROOT).child(1).build(),
				CommandNode.builder()
						.type(me.whereareiam.anvil.protocol.java.api.type.CommandNodeType.LITERAL)
						.name("give")
						.child(2)
						.child(3)
						.restricted(true)
						.build(),
				CommandNode.builder()
						.type(me.whereareiam.anvil.protocol.java.api.type.CommandNodeType.ARGUMENT)
						.name("amount")
						.redirect(0)
						.executable(true)
						.parser("brigadier:integer")
						.min(1D)
						.max(64D)
						.suggestions("minecraft:ask_server")
						.build(),
				CommandNode.builder()
						.type(me.whereareiam.anvil.protocol.java.api.type.CommandNodeType.ARGUMENT)
						.name("text")
						.executable(true)
						.parser("brigadier:string")
						.stringMode(StringArgumentMode.GREEDY_PHRASE)
						.build()
		), 0));

		CommandTree tree = (CommandTree) player.payloads.getLast();
		assertEquals(0, tree.getRoot());
		assertEquals(CommandTreeNode.builder().type(CommandNodeType.ROOT).name("").child(1).parser("").build(), tree.getNodes().get(0));
		assertEquals(CommandTreeNode.builder().type(CommandNodeType.LITERAL).name("give").child(2).child(3).restricted(true).parser("")
				.build(), tree.getNodes().get(1));
		assertEquals(CommandTreeNode.builder().type(CommandNodeType.ARGUMENT).name("amount").redirect(0).executable(true)
				.parser("brigadier:integer").min(1D).max(64D).build(), tree.getNodes().get(2));
		assertEquals(CommandTreeNode.builder().type(CommandNodeType.ARGUMENT).name("text").executable(true).parser("brigadier:string")
				.stringMode("GREEDY_PHRASE").build(), tree.getNodes().get(3));
		assertEquals(tree.getNodes().get(1), tree.literal("give").orElseThrow());
	}

	@Test
	void stopsListeningAndObservingConnectionsWhenClosed() {
		RecordingProtocol protocol = new RecordingProtocol();
		RecordingPlayer player = new RecordingPlayer(protocol);
		WorkerBinding binding = extension.bind(player, new RecordingOperations());

		binding.close();
		player.startConnection();
		protocol.deliver(EMPTY);

		assertEquals(Map.of(), protocol.listeners);
		assertNull(player.connections);
		assertEquals(List.of(), player.events);
	}

	@Test
	void isUnavailableForAPlayerThatDoesNotSpeakTheJavaProtocol() {
		RecordingPlayer player = new RecordingPlayer(null);

		assertThrows(ProtocolUnavailableException.class, () -> extension.bind(player, new RecordingOperations()));
		assertNull(player.connections);
	}

	/**
	 * Hands delivered packets to the listener of their class; commands send nothing.
	 */
	private static final class RecordingProtocol implements JavaProtocol {
		private final Map<Class<?>, Consumer<Object>> listeners = new LinkedHashMap<>();

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
			throw new AssertionError("Commands send no packets");
		}

		@Override
		public boolean supports(@NotNull Class<? extends JavaPacket> type) {
			return true;
		}

		private void deliver(ClientboundPacket packet) {
			Consumer<Object> listener = listeners.get(packet.getClass());
			if (listener != null) listener.accept(packet);
		}
	}

	private static final class RecordingOperations implements OperationRegistry {
		private final Map<String, Function<?, ?>> handlers = new LinkedHashMap<>();

		@Override
		public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
			handlers.put(operation.getId(), handler);
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
			throw new AssertionError("Commands do not drive the lifecycle");
		}

		@Override
		public void disconnect() {
			throw new AssertionError("Commands do not drive the lifecycle");
		}

		@Override
		public void rejoin() {
			throw new AssertionError("Commands do not drive the lifecycle");
		}

		@Override
		public @NotNull Object nativeSession() {
			throw new AssertionError("Commands use the protocol, not the native session");
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			throw new AssertionError("Commands use the protocol, not the native session");
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			connections = listener;
			return () -> connections = null;
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			throw new AssertionError("Commands do not use the view rotation");
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			throw new AssertionError("Commands do not change the view rotation");
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			events.add(event.getId());
			payloads.add(payload);
		}
	}
}
