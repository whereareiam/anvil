package me.whereareiam.anvil.capability.binding;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
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
		var capabilities = new WorkerCapabilities<>("external", String.class, 1, List.of(extension));
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
		var capabilities = new WorkerCapabilities<>("external", String.class, 1, List.of(
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
		var capabilities = new WorkerCapabilities<>("external", String.class, 1, List.of(
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
			var capabilities = new WorkerCapabilities<>("external", String.class, 1, List.of(
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
		var capabilities = new WorkerCapabilities<>("external", String.class, 1, List.of(
				new Extension("first", (player, operations) -> () -> { closed.add("first"); throw cleanup; }),
				new Extension("second", (player, operations) -> () -> closed.add("second")),
				new Extension("broken", (player, operations) -> {
					operations.register(COUNT, ignored -> 1);
					throw new AssertionError("The delegate must reject registration");
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
		assertEquals(List.of("second", "first"), closed);
		assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
	}

	@Test
	void errorsRollbackEveryTypedBindingAndPreserveTheFirstFailure() {
		List<String> closed = new ArrayList<>();
		AssertionError original = new AssertionError("bind failed");
		LinkageError cleanup = new LinkageError("cleanup failed");
		var capabilities = new WorkerCapabilities<>("external", String.class, 1, List.of(
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
	private static final class Extension implements WorkerExtension<String> {
		private final String id;
		private final BiFunction<PlayerBindingContext<String>, OperationRegistry, WorkerBinding> binding;
		public @NotNull String id() { return id; }
		public @NotNull String backendId() { return "external"; }
		public @NotNull Class<String> backendType() { return String.class; }
		public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<String> player, @NotNull OperationRegistry operations) { return binding.apply(player, operations); }
	}

	private static final class Player implements PlayerBindingContext<String> {
		public @NotNull String name() { return "Alice"; }
		public @NotNull UUID uniqueId() { return new UUID(0, 1); }
		public void connect() { throw new AssertionError(); }
		public void disconnect() { throw new AssertionError(); }
		public void rejoin() { throw new AssertionError(); }
		public @NotNull String backend() { return "native"; }
		public boolean isCurrentBackend(@NotNull String backend) { return backend.equals("native"); }
		public @NotNull Subscription bindBackend(@NotNull Function<String, Subscription> listener) { return listener.apply(backend()); }
		public @NotNull ViewRotation viewRotation() { return new ViewRotation(0, 0); }
		public void viewRotation(@NotNull ViewRotation rotation) { throw new AssertionError(); }
		public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, E payload) { throw new AssertionError(); }
	}
}
