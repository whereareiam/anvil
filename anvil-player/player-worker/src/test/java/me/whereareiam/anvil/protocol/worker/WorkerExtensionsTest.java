package me.whereareiam.anvil.protocol.worker;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.api.model.player.ViewRotation;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerExtensionsTest {
	private static final String LIBRARY = "test";
	private static final Set<String> RESERVED = Set.of("create", "destroy", "shutdown");
	private static final OperationDescriptor<Void, Integer> COUNT =
			new OperationDescriptor<>("example.count", Void.class, Integer.class);
	private static final OperationDescriptor<String, String> ECHO =
			new OperationDescriptor<>("example.echo", String.class, String.class);
	private static final EventDescriptor<String> CHANGED = new EventDescriptor<>("example.changed", String.class);

	@Test
	void selectsExtensionsByLibraryAndAssignableNativeSessionType() {
		var extensions = extensions(String.class, List.of(
				new Extension("exact", Optional.of(LIBRARY), String.class),
				new Extension("any-library", Optional.empty(), Object.class),
				new Extension("supertype", Optional.of(LIBRARY), CharSequence.class),
				new Extension("other-library", Optional.of("other"), String.class),
				new Extension("other-session", Optional.of(LIBRARY), Integer.class)
		));

		assertEquals(Set.of("exact", "any-library", "supertype"), extensions.capabilities());
		String reason = "requires native session java.lang.Integer, but the test worker provides java.lang.String";
		assertEquals(Map.of("other-session", reason), extensions.unavailable());
	}

	@Test
	void rejectsTwoExtensionsOfThisWorkerWithTheSameId() {
		assertThrows(IllegalStateException.class, () -> extensions(List.of(counter(), counter())));
		assertThrows(IllegalStateException.class, () -> extensions(List.of(
				new Extension("same", Optional.empty(), Object.class),
				new Extension("same", Optional.of(LIBRARY), Object.class)
		)));
	}

	@Test
	void reportsAnExtensionWhoseSessionTypeIsMissingFromTheLoadedRelease() {
		var missing = new Extension("missing-session", Optional.of(LIBRARY), Object.class) {
			@Override
			public @NotNull Class<Object> nativeSessionType() {
				throw new NoClassDefFoundError("org/example/ClientSession");
			}
		};
		var extensions = extensions(List.of(missing, counter()));

		assertEquals(Set.of("counter"), extensions.capabilities());
		assertEquals(Map.of("missing-session", "NoClassDefFoundError: org/example/ClientSession"),
				extensions.unavailable());
	}

	@Test
	void discoversTheExtensionsDeclaredOnTheClassPath() {
		var extensions = WorkerExtensions.discover(LIBRARY, Object.class, RESERVED);

		assertTrue(extensions.capabilities().contains(DiscoveredExtension.ID), extensions.capabilities().toString());
	}

	@Test
	void bindsIndependentStateForEachPlayer() {
		var extensions = extensions(List.of(counter()));
		try (var first = bind(extensions, new Player("Alice")); var second = bind(extensions, new Player("Bob"))) {
			assertEquals(1, count(first));
			assertEquals(2, count(first));
			assertEquals(1, count(second));
		}
	}

	@Test
	void bindingsSeeTheirOwnPlayerAndReleaseItsNativeListenersWhenClosed() {
		List<String> listeners = new ArrayList<>();
		var extensions = extensions(List.of(new Extension("context", (player, operations) -> {
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
		var alice = new Player("Alice");
		var bob = new Player("Bob");

		try (var aliceBindings = bind(extensions, alice); var bobBindings = bind(extensions, bob)) {
			alice.connect();
			assertEquals("Alice:hello", execute(aliceBindings, ECHO, "hello"));
			assertEquals("Bob:hello", execute(bobBindings, ECHO, "hello"));
			assertEquals(List.of("Alice:hello"), alice.events);
			assertEquals(List.of("Bob:hello"), bob.events);
			assertEquals(new ViewRotation(56, 78), alice.viewRotation());
			assertEquals(List.of("Alice attached"), listeners);
		}
		assertEquals(List.of("Alice attached", "Alice detached"), listeners);
	}

	@Test
	void acceptsOperationsOnlyWhileBindingThePlayer() {
		AtomicReference<OperationRegistry> captured = new AtomicReference<>();
		AtomicInteger closed = new AtomicInteger();
		var extensions = extensions(List.of(new Extension("counter", (player, operations) -> {
			captured.set(operations);
			operations.register(COUNT, ignored -> 1);
			return closed::incrementAndGet;
		})));
		var late = new OperationDescriptor<>("example.late", Void.class, Integer.class);

		var bindings = bind(extensions, new Player("Alice"));
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
		var extensions = extensions(List.of(
				new Extension("first", (player, operations) -> closed::incrementAndGet),
				new Extension("reserved", (player, operations) -> {
					operations.register(new OperationDescriptor<>(operation, Void.class, Integer.class), ignored -> 1);
					return () -> { };
				})
		));

		assertThrows(IllegalArgumentException.class, () -> bind(extensions, new Player("Alice")));
		assertEquals(1, closed.get());
	}

	@Test
	void rejectsDuplicateOperationIdsEvenWhenTheirSchemasDiffer() {
		AtomicInteger closed = new AtomicInteger();
		var extensions = extensions(List.of(
				new Extension("counter", (player, operations) -> {
					operations.register(COUNT, ignored -> 1);
					return closed::incrementAndGet;
				}),
				new Extension("duplicate", (player, operations) -> {
					var conflicting = new OperationDescriptor<>(COUNT.getId(), String.class, String.class);
					operations.register(conflicting, Function.identity());
					return () -> { };
				})
		));

		var failure = assertThrows(IllegalStateException.class, () -> bind(extensions, new Player("Alice")));
		assertTrue(failure.getMessage().contains(COUNT.getId()), failure.getMessage());
		assertEquals(1, closed.get());
	}

	@Test
	void unavailableAdaptersMakeTheExtensionUnavailableWithThePlayersReason() {
		var extensions = extensions(List.of(
				counter(),
				new Extension("adapted", (player, operations) -> {
					operations.register(ECHO, Function.identity());
					player.adapter(Runnable.class);
					return () -> { };
				})
		));

		try (var bindings = bind(extensions, new Player("Alice"))) {
			assertEquals(1, count(bindings));
			assertThrows(IllegalArgumentException.class, () -> bindings.operation(ECHO.getId()));
			assertEquals(Set.of("counter"), extensions.capabilities());
			assertEquals(Map.of("adapted", "no adapter for java.lang.Runnable"), extensions.unavailable());
		}
	}

	@Test
	void linkageFailuresMakeTheExtensionUnavailableWithoutFailingThePlayer() {
		AtomicInteger attempts = new AtomicInteger();
		var extensions = extensions(List.of(
				counter(),
				new Extension("linked", (player, operations) -> {
					attempts.incrementAndGet();
					throw new NoSuchMethodError("'void example.Packet.<init>(int)'");
				})
		));
		assertEquals(Set.of("counter", "linked"), extensions.capabilities());

		try (var first = bind(extensions, new Player("Alice")); var second = bind(extensions, new Player("Bob"))) {
			assertEquals(1, count(first));
			assertEquals(1, count(second));
			assertEquals(1, attempts.get(), "An unavailable capability is not bound for later players");
			assertEquals(Set.of("counter"), extensions.capabilities());
			assertEquals(Map.of("linked", "NoSuchMethodError: 'void example.Packet.<init>(int)'"),
					extensions.unavailable());
		}
	}

	@Test
	void linkageFailuresDropTheExtensionsOperationsAndReleaseItsNativeListeners() {
		List<String> listeners = new ArrayList<>();
		var extensions = extensions(List.of(
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
		var player = new Player("Alice");
		player.connect();

		try (var bindings = bind(extensions, player)) {
			assertEquals(List.of("attached", "detached"), listeners);
			player.rejoin();
			assertEquals(List.of("attached", "detached"), listeners);
			assertEquals(1, count(bindings));
			var rejected = assertThrows(IllegalArgumentException.class, () -> bindings.operation(ECHO.getId()));
			assertTrue(rejected.getMessage().contains(ECHO.getId()));
			assertEquals(Map.of("partial", "NoClassDefFoundError: org/example/MissingPacket"),
					extensions.unavailable());
			assertEquals(Set.of("counter"), extensions.capabilities());
		}
	}

	@Test
	void serviceLookupFailuresMakeOnlyTheirExtensionUnavailable() {
		AtomicInteger attempts = new AtomicInteger();
		var extensions = extensions(List.of(
				new Extension("looked-up", (player, operations) -> {
					attempts.incrementAndGet();
					operations.register(ECHO, Function.identity());
					throw new ServiceConfigurationError("org.example.Port: Provider org.example.MissingPort not found");
				}),
				counter()
		));

		try (var first = bind(extensions, new Player("Alice")); var second = bind(extensions, new Player("Bob"))) {
			assertEquals(1, count(first));
			assertEquals(1, count(second));
			assertThrows(IllegalArgumentException.class, () -> first.operation(ECHO.getId()));
			assertEquals(1, attempts.get(), "An unavailable capability is not bound for later players");
			assertEquals(Set.of("counter"), extensions.capabilities());
			String reason = "ServiceConfigurationError: org.example.Port: Provider org.example.MissingPort not found";
			assertEquals(Map.of("looked-up", reason), extensions.unavailable());
		}
	}

	@Test
	void errorsDuringBindingReleaseEveryAcquiredBindingAndPreserveTheFirstFailure() {
		List<String> closed = new ArrayList<>();
		AssertionError original = new AssertionError("binding failed");
		LinkageError cleanup = new LinkageError("cleanup failed");
		var extensions = extensions(List.of(
				new Extension("first", (player, operations) -> () -> closed.add("first")),
				new Extension("second", (player, operations) -> () -> {
					closed.add("second");
					throw cleanup;
				}),
				new Extension("broken", (player, operations) -> {
					player.bindNativeSession(session -> () -> closed.add("broken listener"));
					throw original;
				})
		));
		var player = new Player("Alice");
		player.connect();

		assertSame(original, assertThrows(AssertionError.class, () -> bind(extensions, player)));
		assertEquals(List.of("broken listener", "second", "first"), closed);
		assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
		assertEquals(Set.of("first", "second", "broken"), extensions.capabilities());
	}

	@Test
	void linkageFailuresReleaseTheProtocolListenersTheExtensionAdded() {
		List<String> received = new ArrayList<>();
		var extensions = extensions(List.of(
				new Extension("partial", (player, operations) -> {
					player.protocol(Greetings.class).listen(greeting -> received.add("partial " + greeting));
					throw new NoClassDefFoundError("org/example/MissingPacket");
				}),
				new Extension("listening", (player, operations) -> player.protocol(Greetings.class)
						.listen(greeting -> received.add("listening " + greeting))::close)
		));
		var player = new Player("Alice");

		try (var ignored = bind(extensions, player)) {
			player.greetings.greet("welcome");

			assertEquals(List.of("listening welcome"), received);
			assertEquals(Set.of("listening"), extensions.capabilities());
			assertEquals(Map.of("partial", "NoClassDefFoundError: org/example/MissingPacket"),
					extensions.unavailable());
		}
	}

	@Test
	void anExtensionAskingForAProtocolThePlayerDoesNotSpeakIsUnavailable() {
		var extensions = extensions(List.of(
				counter(),
				new Extension("other-protocol", (player, operations) -> {
					player.protocol(Runnable.class);
					return () -> { };
				})
		));

		try (var ignored = bind(extensions, new Player("Alice"))) {
			assertEquals(Set.of("counter"), extensions.capabilities());
			String reason = "the test worker speaks " + Greetings.class.getName() + ", not java.lang.Runnable";
			assertEquals(Map.of("other-protocol", reason), extensions.unavailable());
		}
	}

	private static WorkerExtensions extensions(List<? extends WorkerExtension<?>> candidates) {
		return extensions(Object.class, candidates);
	}

	private static WorkerExtensions extensions(Class<?> sessionType, List<? extends WorkerExtension<?>> candidates) {
		return new WorkerExtensions(LIBRARY, sessionType, RESERVED, candidates);
	}

	private static WorkerExtensions.PlayerBindings bind(WorkerExtensions extensions, Player player) {
		return extensions.bind(player, player.protocols);
	}

	private static int count(WorkerExtensions.PlayerBindings bindings) {
		return execute(bindings, COUNT, null);
	}

	@SuppressWarnings("unchecked")
	private static <Q, R> R execute(
			WorkerExtensions.PlayerBindings bindings,
			OperationDescriptor<Q, R> descriptor,
			@Nullable Q request
	) {
		var operation = (WorkerOperation<Q, R>) bindings.operation(descriptor.getId());
		assertEquals(descriptor, operation.getDescriptor());

		return operation.apply(request);
	}

	private static Extension counter() {
		return new Extension("counter", (player, operations) -> {
			AtomicInteger count = new AtomicInteger();
			operations.register(COUNT, ignored -> count.incrementAndGet());
			return () -> { };
		});
	}

	/**
	 * A protocol a test player speaks: greetings it hands to its listeners.
	 */
	private interface Greetings {
		Subscription listen(Consumer<String> listener);
	}

	private static final class GreetingChannel implements Greetings {
		private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

		@Override
		public Subscription listen(Consumer<String> listener) {
			listeners.add(listener);
			return () -> listeners.remove(listener);
		}

		private Greetings scoped(Consumer<Subscription> track) {
			return listener -> {
				Subscription subscription = listen(listener);
				track.accept(subscription);
				return subscription;
			};
		}

		private void greet(String greeting) {
			listeners.forEach(listener -> listener.accept(greeting));
		}
	}

	/**
	 * A library's player, reduced to what extensions bind to: sessions as plain objects, events recorded.
	 */
	@RequiredArgsConstructor
	private static final class Player implements PlayerBindingContext<Object> {
		private final String name;
		private final List<String> events = new ArrayList<>();
		private final NativeSessionBindings sessions = new NativeSessionBindings();
		private final GreetingChannel greetings = new GreetingChannel();
		private final WorkerProtocols protocols = WorkerProtocols.builder(LIBRARY)
				.speak(Greetings.class, greetings, greetings::scoped)
				.build();
		private @Nullable Object session;
		private ViewRotation rotation = new ViewRotation(0, 0);

		@Override
		public @NotNull String name() {
			return name;
		}

		@Override
		public @NotNull UUID uniqueId() {
			return UUID.nameUUIDFromBytes(name.getBytes());
		}

		@Override
		public void connect() {
			session = new Object();
			sessions.attach(session);
		}

		@Override
		public void disconnect() {
			if (session != null) sessions.end(session);
			session = null;
		}

		@Override
		public void rejoin() {
			disconnect();
			connect();
		}

		@Override
		public @NotNull Object nativeSession() {
			if (session == null) throw new IllegalStateException(name + " is not connected");

			return session;
		}

		@Override
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
			return session == nativeSession;
		}

		@Override
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
			return sessions.register(listener);
		}

		@Override
		public @NotNull ViewRotation viewRotation() {
			return rotation;
		}

		@Override
		public void viewRotation(@NotNull ViewRotation rotation) {
			this.rotation = rotation;
		}

		@Override
		public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
			events.add(String.valueOf(payload));
		}

		@Override
		public <P> @NotNull P protocol(@NotNull Class<P> type) {
			return protocols.protocol(type);
		}

		@Override
		public <P> @NotNull P adapter(@NotNull Class<P> port) {
			throw new ProtocolUnavailableException("no adapter for " + port.getName());
		}
	}

	@RequiredArgsConstructor
	private static class Extension implements WorkerExtension<Object> {
		private final String id;
		private final Optional<String> libraryId;
		private final Class<?> sessionType;
		private final BiFunction<PlayerBindingContext<Object>, OperationRegistry, WorkerBinding> binding;

		private Extension(
				String id,
				BiFunction<PlayerBindingContext<Object>, OperationRegistry, WorkerBinding> binding
		) {
			this(id, Optional.of(LIBRARY), Object.class, binding);
		}

		private Extension(String id, Optional<String> libraryId, Class<?> sessionType) {
			this(id, libraryId, sessionType, (player, operations) -> () -> { });
		}

		@Override
		public @NotNull String id() {
			return id;
		}

		@Override
		public @NotNull Optional<String> libraryId() {
			return libraryId;
		}

		@Override
		@SuppressWarnings("unchecked")
		public @NotNull Class<Object> nativeSessionType() {
			return (Class<Object>) sessionType;
		}

		@Override
		public @NotNull WorkerBinding bind(
				@NotNull PlayerBindingContext<Object> player,
				@NotNull OperationRegistry operations
		) {
			return binding.apply(player, operations);
		}
	}
}
