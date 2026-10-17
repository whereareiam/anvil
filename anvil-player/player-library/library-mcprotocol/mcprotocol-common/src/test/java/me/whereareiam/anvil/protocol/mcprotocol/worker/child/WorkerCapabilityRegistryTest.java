package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.chat.ClientboundSystemChat;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerConnection;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerProfile;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.EventWorkerExtension;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.ProbeWorkerExtension;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.RecordingClient;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class WorkerCapabilityRegistryTest {
	private static final OperationDescriptor<Void, Integer> COUNT = new OperationDescriptor<>("example.count", Void.class, Integer.class);
	private static final OperationDescriptor<String, String> ECHO = new OperationDescriptor<>("example.echo", String.class, String.class);
	private static final EventDescriptor<String> CHANGED = new EventDescriptor<>("example.changed", String.class);

	private final WorkerMessageCodec codec = new WorkerMessageCodec();

	@Test
	void selectsExtensionsByLibraryAndAssignableNativeSessionType() {
		var registry = new WorkerCapabilityRegistry(String.class, List.of(
				new Extension("exact", Optional.of("mcprotocol"), String.class),
				new Extension("any-library", Optional.empty(), Object.class),
				new Extension("supertype", Optional.of("mcprotocol"), CharSequence.class),
				new Extension("other-library", Optional.of("other"), String.class),
				new Extension("other-session", Optional.of("mcprotocol"), Integer.class)
		));

		assertEquals(Set.of("exact", "any-library", "supertype"), registry.capabilities());
		assertEquals(Map.of("other-session", "requires native session java.lang.Integer, but the mcprotocol worker provides java.lang.String"),
				registry.unavailable());
	}

	@Test
	void rejectsTwoExtensionsOfThisWorkerWithTheSameId() {
		assertThrows(IllegalStateException.class, () -> new WorkerCapabilityRegistry(Object.class, List.of(counter(), counter())));
		assertThrows(IllegalStateException.class, () -> new WorkerCapabilityRegistry(Object.class, List.of(
				new Extension("same", Optional.empty(), Object.class),
				new Extension("same", Optional.of("mcprotocol"), Object.class)
		)));
	}

	@Test
	void reportsAnExtensionWhoseSessionTypeIsMissingFromTheLoadedRelease() {
		var missing = new Extension("missing-session", Optional.of("mcprotocol"), Object.class) {
			@Override
			public @NotNull Class<Object> nativeSessionType() {
				throw new NoClassDefFoundError("org/example/ClientSession");
			}
		};
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(missing, counter()));

		assertEquals(Set.of("counter"), registry.capabilities());
		assertEquals(Map.of("missing-session", "NoClassDefFoundError: org/example/ClientSession"), registry.unavailable());
	}

	@Test
	void discoversTheExtensionsDeclaredOnTheClassPath() {
		var registry = WorkerCapabilityRegistry.discover(Object.class);

		assertTrue(registry.capabilities().containsAll(Set.of(EventWorkerExtension.ID, ProbeWorkerExtension.ID)), registry.capabilities().toString());
	}

	@Test
	void bindsIndependentStateForEachPlayer() {
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(counter()));
		try (var first = registry.bind(player(registry)); var second = registry.bind(player(registry))) {
			assertEquals(1, count(first));
			assertEquals(2, count(first));
			assertEquals(1, count(second));
		}
	}

	@Test
	void bindingsSeeTheirOwnPlayerAndReleaseItsNativeListenersWhenClosed() {
		List<String> listeners = new ArrayList<>();
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(new Extension("context", (player, operations) -> {
			assertEquals(new ViewRotation(0, 0), player.viewRotation());
			player.viewRotation(new ViewRotation(56, 78));
			operations.register(ECHO, request -> {
				String result = player.name() + ":" + request;
				player.emit(CHANGED, result);
				return result;
			});
			var subscription = player.bindNativeSession(session -> {
				assertTrue(player.isCurrentNativeSession(session));
				listeners.add(player.name() + " attached");
				return () -> listeners.add(player.name() + " detached");
			});
			return subscription::close;
		})));
		var aliceEvents = new ByteArrayOutputStream();
		var bobEvents = new ByteArrayOutputStream();

		try (var alice = player(registry, "Alice", aliceEvents); var bob = player(registry, "Bob", bobEvents)) {
			try (var aliceBindings = registry.bind(alice); var bobBindings = registry.bind(bob)) {
				alice.connect();
				assertEquals("Alice:hello", codec.open(aliceBindings.execute(ECHO.getId(), codec.envelope("hello")), String.class));
				assertEquals("Bob:hello", codec.open(bobBindings.execute(ECHO.getId(), codec.envelope("hello")), String.class));
				assertEquals(List.of("Alice:hello"), events(aliceEvents));
				assertEquals(List.of("Bob:hello"), events(bobEvents));
				assertEquals(new ViewRotation(56, 78), alice.viewRotation());
				assertEquals(List.of("Alice attached"), listeners);
			}
			assertEquals(List.of("Alice attached", "Alice detached"), listeners);
		}
	}

	@Test
	void acceptsOperationsOnlyWhileBindingThePlayer() {
		AtomicReference<OperationRegistry> captured = new AtomicReference<>();
		AtomicInteger closed = new AtomicInteger();
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(new Extension("counter", (player, operations) -> {
			captured.set(operations);
			operations.register(COUNT, ignored -> 1);
			return closed::incrementAndGet;
		})));
		var late = new OperationDescriptor<>("example.late", Void.class, Integer.class);

		var bindings = registry.bind(player(registry));
		assertThrows(IllegalStateException.class, () -> captured.get().register(late, ignored -> 2));
		bindings.close();
		bindings.close();
		assertEquals(1, closed.get());
		assertThrows(IllegalStateException.class, () -> captured.get().register(late, ignored -> 2));
	}

	@ParameterizedTest
	@ValueSource(strings = {"create", "destroy", "shutdown", "unqualified", "", " "})
	void rejectsReservedAndUnqualifiedOperationsAndReleasesEarlierBindings(String operation) {
		AtomicInteger closed = new AtomicInteger();
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(
				new Extension("first", (player, operations) -> closed::incrementAndGet),
				new Extension("reserved", (player, operations) -> {
					operations.register(new OperationDescriptor<>(operation, Void.class, Integer.class), ignored -> 1);
					return () -> { };
				})
		));

		assertThrows(IllegalArgumentException.class, () -> registry.bind(player(registry)));
		assertEquals(1, closed.get());
	}

	@Test
	void rejectsDuplicateOperationIdsEvenWhenTheirSchemasDiffer() {
		AtomicInteger closed = new AtomicInteger();
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(
				new Extension("counter", (player, operations) -> {
					operations.register(COUNT, ignored -> 1);
					return closed::incrementAndGet;
				}),
				new Extension("duplicate", (player, operations) -> {
					operations.register(new OperationDescriptor<>(COUNT.getId(), String.class, String.class), Function.identity());
					return () -> { };
				})
		));

		var failure = assertThrows(IllegalStateException.class, () -> registry.bind(player(registry)));
		assertTrue(failure.getMessage().contains(COUNT.getId()), failure.getMessage());
		assertEquals(1, closed.get());
	}

	@Test
	void unavailableAdaptersMakeTheExtensionUnavailableWithTheWorkersReason() {
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(
				counter(),
				new Extension("adapted", (player, operations) -> {
					operations.register(ECHO, Function.identity());
					player.adapter(Runnable.class);
					return () -> { };
				})
		));

		try (var bindings = registry.bind(player(registry))) {
			assertEquals(1, count(bindings));
			assertThrows(IllegalArgumentException.class, () -> bindings.execute(ECHO.getId(), codec.envelope("hello")));
			assertEquals(Set.of("counter"), registry.capabilities());
			assertEquals(Map.of("adapted", "no mcprotocol segment selected for Minecraft 1.21.11 provides an adapter for java.lang.Runnable"),
					registry.unavailable());
		}
	}

	@Test
	void linkageFailuresMakeTheExtensionUnavailableWithoutFailingThePlayer() {
		AtomicInteger attempts = new AtomicInteger();
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(
				counter(),
				new Extension("linked", (player, operations) -> {
					attempts.incrementAndGet();
					throw new NoSuchMethodError("'void example.Packet.<init>(int)'");
				})
		));
		assertEquals(Set.of("counter", "linked"), registry.capabilities());

		try (var first = registry.bind(player(registry)); var second = registry.bind(player(registry))) {
			assertEquals(1, count(first));
			assertEquals(1, count(second));
			assertEquals(1, attempts.get(), "An unavailable capability is not bound for later players");
			assertEquals(Set.of("counter"), registry.capabilities());
			assertEquals(Map.of("linked", "NoSuchMethodError: 'void example.Packet.<init>(int)'"), registry.unavailable());
			assertEquals(registry.unavailable(), registry.report().getUnavailable());
		}
	}

	@Test
	void linkageFailuresDropTheExtensionsOperationsAndReleaseItsNativeListeners() {
		List<String> listeners = new ArrayList<>();
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(
				new Extension("partial", (player, operations) -> {
					operations.register(ECHO, Function.identity());
					player.bindNativeSession(session -> {
						listeners.add("attached");
						return () -> listeners.add("detached");
					});
					throw new NoClassDefFoundError("org/example/MissingPacket");
				}),
				counter()
		));

		try (var player = player(registry)) {
			player.connect();
			try (var bindings = registry.bind(player)) {
				assertEquals(List.of("attached", "detached"), listeners);
				player.rejoin();
				assertEquals(List.of("attached", "detached"), listeners);
				assertEquals(1, count(bindings));
				IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
						() -> bindings.execute(ECHO.getId(), codec.envelope("hello")));
				assertTrue(rejected.getMessage().contains(ECHO.getId()));
				assertEquals(Map.of("partial", "NoClassDefFoundError: org/example/MissingPacket"), registry.unavailable());
				assertEquals(Set.of("counter"), registry.capabilities());
			}
		}
	}

	@Test
	void serviceLookupFailuresMakeOnlyTheirExtensionUnavailable() {
		AtomicInteger attempts = new AtomicInteger();
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(
				new Extension("looked-up", (player, operations) -> {
					attempts.incrementAndGet();
					operations.register(ECHO, Function.identity());
					throw new ServiceConfigurationError("org.example.Port: Provider org.example.MissingPort not found");
				}),
				counter()
		));

		try (var first = registry.bind(player(registry)); var second = registry.bind(player(registry))) {
			assertEquals(1, count(first));
			assertEquals(1, count(second));
			assertThrows(IllegalArgumentException.class, () -> first.execute(ECHO.getId(), codec.envelope("hello")));
			assertEquals(1, attempts.get(), "An unavailable capability is not bound for later players");
			assertEquals(Set.of("counter"), registry.capabilities());
			assertEquals(Map.of("looked-up", "ServiceConfigurationError: org.example.Port: Provider org.example.MissingPort not found"),
					registry.unavailable());
		}
	}

	@Test
	void errorsDuringBindingReleaseEveryAcquiredBindingAndPreserveTheFirstFailure() {
		List<String> closed = new ArrayList<>();
		AssertionError original = new AssertionError("binding failed");
		LinkageError cleanup = new LinkageError("cleanup failed");
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(
				new Extension("first", (player, operations) -> () -> closed.add("first")),
				new Extension("second", (player, operations) -> () -> { closed.add("second"); throw cleanup; }),
				new Extension("broken", (player, operations) -> {
					player.bindNativeSession(session -> () -> closed.add("broken listener"));
					throw original;
				})
		));

		try (var player = player(registry)) {
			player.connect();
			assertSame(original, assertThrows(AssertionError.class, () -> registry.bind(player)));
		}
		assertEquals(List.of("broken listener", "second", "first"), closed);
		assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
		assertEquals(Set.of("first", "second", "broken"), registry.capabilities());
	}

	@Test
	void linkageFailuresReleaseTheProtocolListenersTheExtensionAdded() {
		List<String> received = new ArrayList<>();
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(
				new Extension("partial", (player, operations) -> {
					player.protocol(JavaProtocol.class).listen(ClientboundSystemChat.class, packet -> received.add("partial " + packet.getText()));
					throw new NoClassDefFoundError("org/example/MissingPacket");
				}),
				new Extension("listening", (player, operations) -> player.protocol(JavaProtocol.class)
						.listen(ClientboundSystemChat.class, packet -> received.add("listening " + packet.getText()))::close)
		));
		var client = new RecordingClient();

		try (var player = player(registry, "Alice", OutputStream.nullOutputStream(), client)) {
			player.initialize();
			player.connect();
			Object session = client.sessions().getFirst();
			client.listener(session).received(session, new ClientboundSystemChat("welcome", false));

			assertEquals(List.of("listening welcome"), received);
			assertEquals(Set.of("listening"), registry.capabilities());
			assertEquals(Map.of("partial", "NoClassDefFoundError: org/example/MissingPacket"), registry.unavailable());
		}
	}

	@Test
	void anExtensionAskingForAnotherProtocolIsUnavailableWithTheWorkersReason() {
		var registry = new WorkerCapabilityRegistry(Object.class, List.of(
				counter(),
				new Extension("other-protocol", (player, operations) -> {
					player.protocol(Runnable.class);
					return () -> { };
				})
		));

		try (var player = player(registry)) {
			player.initialize();

			assertEquals(Set.of("counter"), registry.capabilities());
			assertEquals(Map.of("other-protocol", "the mcprotocol worker speaks " + JavaProtocol.class.getName() + ", not java.lang.Runnable"),
					registry.unavailable());
		}
	}

	private int count(WorkerCapabilityRegistry.PlayerBindings bindings) {
		return codec.open(bindings.execute(COUNT.getId(), codec.envelope(null)), Integer.class);
	}

	private List<String> events(ByteArrayOutputStream output) {
		return output.toString(StandardCharsets.UTF_8).lines()
				.map(line -> assertInstanceOf(WorkerEvent.class, codec.decodeMessage(line).orElseThrow()))
				.peek(event -> assertEquals(CHANGED.getId(), event.getEvent()))
				.map(event -> codec.open(event.getPayload(), String.class))
				.toList();
	}

	private Extension counter() {
		return new Extension("counter", (player, operations) -> {
			AtomicInteger count = new AtomicInteger();
			operations.register(COUNT, ignored -> count.incrementAndGet());
			return () -> { };
		});
	}

	private McProtocolPlayer player(WorkerCapabilityRegistry registry) {
		return player(registry, "Alice", OutputStream.nullOutputStream());
	}

	private McProtocolPlayer player(WorkerCapabilityRegistry registry, String name, OutputStream events) {
		return player(registry, name, events, new RecordingClient());
	}

	private McProtocolPlayer player(WorkerCapabilityRegistry registry, String name, OutputStream events, RecordingClient client) {
		var options = WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder().name(name).uniqueId(UUID.randomUUID()).build())
				.connection(WorkerConnection.builder().host("localhost").port(25565).build())
				.build();

		return new McProtocolPlayer("player", options, client, SegmentRoots.none(),
				new WorkerMessageWriter(new PrintStream(events, true, StandardCharsets.UTF_8)), registry);
	}

	@RequiredArgsConstructor
	private static class Extension implements WorkerExtension<Object> {
		private final String id;
		private final Optional<String> libraryId;
		private final Class<?> sessionType;
		private final BiFunction<PlayerBindingContext<Object>, OperationRegistry, WorkerBinding> binding;

		private Extension(String id, BiFunction<PlayerBindingContext<Object>, OperationRegistry, WorkerBinding> binding) {
			this(id, Optional.of("mcprotocol"), Object.class, binding);
		}

		private Extension(String id, Optional<String> libraryId, Class<?> sessionType) {
			this(id, libraryId, sessionType, (player, operations) -> () -> { });
		}

		public @NotNull String id() { return id; }
		public @NotNull Optional<String> libraryId() { return libraryId; }

		@SuppressWarnings("unchecked")
		public @NotNull Class<Object> nativeSessionType() { return (Class<Object>) sessionType; }

		public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
			return binding.apply(player, operations);
		}
	}
}
