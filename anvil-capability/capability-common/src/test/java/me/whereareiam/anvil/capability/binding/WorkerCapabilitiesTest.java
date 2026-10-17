package me.whereareiam.anvil.capability.binding;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.protocol.api.exception.AdapterUnavailableException;
import me.whereareiam.anvil.capability.protocol.api.model.EventDescriptor;
import me.whereareiam.anvil.capability.protocol.api.model.ViewRotation;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerExtension;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
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

class WorkerCapabilitiesTest {
	private static final ChannelOperation<Void, Integer> COUNT = new ChannelOperation<>(
			"example.count", Void.class, Integer.class
	);

	@Test
	void eachPlayerOwnsItsTypedOperationsAndState() {
		AtomicInteger closed = new AtomicInteger();
		var extension = new Extension("counter", (player, operations) -> {
			var counter = new AtomicInteger();
			operations.register(COUNT, ignored -> counter.incrementAndGet());
			return closed::incrementAndGet;
		});
		var capabilities = new WorkerCapabilities("external", String.class, List.of(extension));
		Registry first = new Registry();
		Registry second = new Registry();
		try (var ignored = capabilities.bind(new Player(), first);
			 var alsoIgnored = capabilities.bind(new Player(), second)
		) {
			assertSame(COUNT, first.registrations.get(COUNT.getId()).operation());
			assertSame(COUNT, second.registrations.get(COUNT.getId()).operation());
			assertEquals(1, first.request(COUNT, null));
			assertEquals(2, first.request(COUNT, null));
			assertEquals(1, second.request(COUNT, null));
			assertEquals(0, closed.get());
		}
		assertEquals(2, closed.get());
	}

	@Test
	void closesRegistrationAfterBindingAndKeepsCleanupIdempotent() {
		AtomicReference<OperationRegistry> captured = new AtomicReference<>();
		AtomicInteger closed = new AtomicInteger();
		var capabilities = new WorkerCapabilities("external", String.class, List.of(
				new Extension("counter", (player, operations) -> {
					captured.set(operations);
					operations.register(COUNT, ignored -> 1);
					return closed::incrementAndGet;
				})
		));
		Registry registry = new Registry();
		var binding = capabilities.bind(new Player(), registry);
		var late = new ChannelOperation<>("example.late", Void.class, Integer.class);

		assertThrows(IllegalStateException.class, () -> captured.get().register(late, ignored -> 2));
		assertEquals(1, registry.forwarded);
		binding.close();
		binding.close();
		assertEquals(1, closed.get());
		assertThrows(IllegalStateException.class, () -> captured.get().register(late, ignored -> 2));
		assertEquals(1, registry.forwarded);
	}

	@Test
	void rejectsDuplicateOperationIdsBeforeForwardingEvenWhenTheirSchemasDiffer() {
		AtomicInteger closed = new AtomicInteger();
		var capabilities = new WorkerCapabilities("external", String.class, List.of(
				new Extension("counter", (player, operations) -> {
					operations.register(COUNT, ignored -> 1);
					return closed::incrementAndGet;
				}),
				new Extension("duplicate", (player, operations) -> {
					operations.register(new ChannelOperation<>(COUNT.getId(), String.class, String.class), Function.identity());
					return () -> { };
				})
		));
		Registry registry = new Registry();

		var failure = assertThrows(IllegalStateException.class, () -> capabilities.bind(new Player(), registry));
		assertTrue(failure.getMessage().contains(COUNT.getId()));
		assertEquals(1, registry.forwarded);
		assertSame(COUNT, registry.registrations.get(COUNT.getId()).operation());
		assertEquals(1, closed.get());
	}

	@Test
	void rejectsMissingNamespacesBeforeForwarding() {
		for (String id : List.of("", " ", "count")) {
			Registry registry = new Registry();
			var capabilities = new WorkerCapabilities("external", String.class, List.of(
					new Extension("counter", (player, operations) -> {
						operations.register(new ChannelOperation<>(id, Void.class, Integer.class), ignored -> 1);
						return () -> { };
					})
			));

			assertThrows(IllegalArgumentException.class, () -> capabilities.bind(new Player(), registry));
			assertEquals(0, registry.forwarded);
		}
	}

	@Test
	void registrationFailuresRollbackEarlierBindingsAndPreserveCleanupFailures() {
		List<String> closed = new ArrayList<>();
		IllegalStateException original = new IllegalStateException("transport registration failed");
		LinkageError cleanup = new LinkageError("cleanup failed");
		var capabilities = new WorkerCapabilities("external", String.class, List.of(
				new Extension("first", (player, operations) -> () -> { closed.add("first"); throw cleanup; }),
				new Extension("second", (player, operations) -> () -> closed.add("second")),
				new Extension("rejected", (player, operations) -> {
					operations.register(COUNT, ignored -> 1);
					return () -> closed.add("rejected");
				})
		));
		OperationRegistry registry = new OperationRegistry() {
			@Override
			public <Q, R> void register(@NotNull ChannelOperation<Q, R> operation, @NotNull Function<Q, R> handler) {
				assertSame(COUNT, operation);
				throw original;
			}
		};

		assertSame(original, assertThrows(IllegalStateException.class, () -> capabilities.bind(new Player(), registry)));
		assertEquals(List.of("rejected", "second", "first"), closed);
		assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
	}

	@Test
	void linkageFailuresMarkOnlyTheirCapabilityUnavailableAndLeaveNothingBound() {
		List<String> events = new ArrayList<>();
		AtomicInteger attempts = new AtomicInteger();
		var late = new ChannelOperation<>("example.late", Void.class, Integer.class);
		var capabilities = new WorkerCapabilities("external", String.class, List.of(
				new Extension("first", (player, operations) -> {
					operations.register(COUNT, ignored -> 1);
					return () -> events.add("close first");
				}),
				new Extension("linked", (player, operations) -> {
					attempts.incrementAndGet();
					operations.register(new ChannelOperation<>("example.linked", Void.class, Integer.class), ignored -> 2);
					player.bindNativeSession(session -> () -> events.add("detach linked"));
					throw new NoClassDefFoundError("org/example/MissingPacket");
				}),
				new Extension("last", (player, operations) -> {
					operations.register(late, ignored -> 3);
					return () -> events.add("close last");
				})
		));
		Registry first = new Registry();
		Registry second = new Registry();

		try (var ignored = capabilities.bind(new Player(), first); var alsoIgnored = capabilities.bind(new Player(), second)) {
			assertEquals(Set.of(COUNT.getId(), late.getId()), first.registrations.keySet());
			assertEquals(Set.of(COUNT.getId(), late.getId()), second.registrations.keySet());
			assertEquals(List.of("detach linked"), events);
			assertEquals(1, attempts.get(), "An unavailable capability is not bound for later players");
			assertEquals(Set.of("first", "last"), capabilities.capabilities());
			assertEquals(Map.of("linked", "NoClassDefFoundError: org/example/MissingPacket"), capabilities.unavailable());
		}
		assertEquals(List.of("detach linked", "close last", "close first", "close last", "close first"), events);
	}

	@Test
	void bindingsReceiveTheWorkersAdaptersAndUnavailableAdaptersMarkOnlyTheirCapabilityUnavailable() {
		List<Runnable> received = new ArrayList<>();
		var capabilities = new WorkerCapabilities("external", String.class, List.of(
				new Extension("adapted", (player, operations) -> {
					received.add(player.adapter(Runnable.class));
					operations.register(COUNT, ignored -> 1);
					return () -> { };
				}),
				new Extension("segmented", (player, operations) -> {
					operations.register(new ChannelOperation<>("example.segmented", Void.class, Integer.class), ignored -> 2);
					player.adapter(Comparable.class);
					return () -> { };
				})
		));
		Registry registry = new Registry();

		try (var ignored = capabilities.bind(new Player(), registry)) {
			assertEquals(List.of(Player.ADAPTER), received);
			assertEquals(Set.of(COUNT.getId()), registry.registrations.keySet());
			assertEquals(Set.of("adapted"), capabilities.capabilities());
			assertEquals(Map.of("segmented", "no external segment provides java.lang.Comparable"), capabilities.unavailable());
		}
	}

	@Test
	void serviceLookupFailuresWhileBindingMarkOnlyTheirCapabilityUnavailable() {
		AtomicInteger attempts = new AtomicInteger();
		var capabilities = new WorkerCapabilities("external", String.class, List.of(
				new Extension("looked-up", (player, operations) -> {
					attempts.incrementAndGet();
					operations.register(new ChannelOperation<>("example.looked-up", Void.class, Integer.class), ignored -> 2);
					throw new ServiceConfigurationError("org.example.Port: Provider org.example.MissingPort not found");
				}),
				new Extension("counter", (player, operations) -> {
					operations.register(COUNT, ignored -> 1);
					return () -> { };
				})
		));
		Registry first = new Registry();
		Registry second = new Registry();

		try (var ignored = capabilities.bind(new Player(), first); var alsoIgnored = capabilities.bind(new Player(), second)) {
			assertEquals(Set.of(COUNT.getId()), first.registrations.keySet());
			assertEquals(Set.of(COUNT.getId()), second.registrations.keySet());
			assertEquals(1, attempts.get(), "An unavailable capability is not bound for later players");
			assertEquals(Set.of("counter"), capabilities.capabilities());
			assertEquals(Map.of("looked-up", "ServiceConfigurationError: org.example.Port: Provider org.example.MissingPort not found"),
					capabilities.unavailable());
		}
	}

	@Test
	void selectsExtensionsByLibraryAndAssignableNativeSessionType() {
		var capabilities = new WorkerCapabilities("external", String.class, List.of(
				new Extension("exact", Optional.of("external"), String.class),
				new Extension("any-library", Optional.empty(), Object.class),
				new Extension("supertype", Optional.of("external"), CharSequence.class),
				new Extension("other-library", Optional.of("other"), String.class),
				new Extension("other-session", Optional.of("external"), Integer.class)
		));

		assertEquals(Set.of("exact", "any-library", "supertype"), capabilities.capabilities());
		assertEquals(Map.of("other-session", "requires native session java.lang.Integer, but the external worker provides java.lang.String"),
				capabilities.unavailable());
		assertThrows(IllegalStateException.class, () -> new WorkerCapabilities("external", String.class, List.of(
				new Extension("same", Optional.empty(), Object.class), new Extension("same", Optional.of("external"), String.class))));
	}

	@Test
	void reportsAnExtensionWhoseSessionTypeIsMissingFromTheLoadedRelease() {
		var missing = new Extension("missing-session", Optional.of("external"), String.class) {
			@Override
			public @NotNull Class<Object> nativeSessionType() {
				throw new NoClassDefFoundError("org/example/ClientSession");
			}
		};
		var capabilities = new WorkerCapabilities("external", String.class, List.of(missing,
				new Extension("exact", Optional.of("external"), String.class)));

		assertEquals(Set.of("exact"), capabilities.capabilities());
		assertEquals(Map.of("missing-session", "NoClassDefFoundError: org/example/ClientSession"), capabilities.unavailable());
	}

	@Test
	void errorsRollbackEveryTypedBindingAndPreserveTheFirstFailure() {
		List<String> closed = new ArrayList<>();
		AssertionError original = new AssertionError("bind failed");
		LinkageError cleanup = new LinkageError("cleanup failed");
		var capabilities = new WorkerCapabilities("external", String.class, List.of(
				new Extension("first", (player, operations) -> () -> closed.add("first")),
				new Extension("second", (player, operations) -> () -> { closed.add("second"); throw cleanup; }),
				new Extension("broken", (player, operations) -> { throw original; })
		));
		assertSame(original, assertThrows(AssertionError.class,
				() -> capabilities.bind(new Player(), new Registry())));
		assertEquals(List.of("second", "first"), closed);
		assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
	}

	private static final class Registry implements OperationRegistry {
		private final Map<String, Registration<?, ?>> registrations = new HashMap<>();
		private int forwarded;

		@Override
		public <Q, R> void register(@NotNull ChannelOperation<Q, R> operation, @NotNull Function<Q, R> handler) {
			forwarded++;
			registrations.put(operation.getId(), new Registration<>(operation, handler));
		}

		@SuppressWarnings("unchecked")
		private <Q, R> R request(ChannelOperation<Q, R> operation, Q request) {
			var registration = (Registration<Q, R>) registrations.get(operation.getId());
			return registration.handler().apply(request);
		}
	}

	private record Registration<Q, R>(ChannelOperation<Q, R> operation, Function<Q, R> handler) { }

	@RequiredArgsConstructor
	private static class Extension implements WorkerExtension<Object> {
		private final String id;
		private final Optional<String> libraryId;
		private final Class<?> sessionType;
		private final BiFunction<PlayerBindingContext<Object>, OperationRegistry, WorkerBinding> binding;

		private Extension(String id, BiFunction<PlayerBindingContext<Object>, OperationRegistry, WorkerBinding> binding) {
			this(id, Optional.of("external"), String.class, binding);
		}

		private Extension(String id, Optional<String> libraryId, Class<?> sessionType) {
			this(id, libraryId, sessionType, (player, operations) -> () -> { });
		}

		public @NotNull String id() { return id; }
		public @NotNull Optional<String> libraryId() { return libraryId; }
		@SuppressWarnings("unchecked")
		public @NotNull Class<Object> nativeSessionType() { return (Class<Object>) sessionType; }
		public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) { return binding.apply(player, operations); }
	}

	private static final class Player implements PlayerBindingContext<Object> {
		private static final Runnable ADAPTER = () -> { };

		public @NotNull String name() { return "Alice"; }
		public @NotNull UUID uniqueId() { return new UUID(0, 1); }
		public void connect() { throw new AssertionError(); }
		public void disconnect() { throw new AssertionError(); }
		public void rejoin() { throw new AssertionError(); }
		public @NotNull Object nativeSession() { return "native"; }
		public boolean isCurrentNativeSession(@NotNull Object nativeSession) { return nativeSession.equals("native"); }
		public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) { return listener.apply(nativeSession()); }
		public @NotNull ViewRotation viewRotation() { return new ViewRotation(0, 0); }
		public void viewRotation(@NotNull ViewRotation rotation) { throw new AssertionError(); }
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, E payload) { throw new AssertionError(); }
		public <P> @NotNull P adapter(@NotNull Class<P> port) {
			if (port == Runnable.class) return port.cast(ADAPTER);
			throw new AdapterUnavailableException("no external segment provides " + port.getName());
		}
	}
}
