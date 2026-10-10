package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.NativeWorkerContext;
import me.whereareiam.anvil.protocol.api.worker.NativeBinding;
import me.whereareiam.anvil.protocol.api.worker.NativeOperations;
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerExtension;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerProvider;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerConnection;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerProfile;
import me.whereareiam.anvil.protocol.mcprotocol.worker.fixture.RecordingClient;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class WorkerCapabilityRegistryTest {
	private static final NativeWorkerContext CONTEXT = NativeWorkerContext.builder()
			.libraryId("mcprotocol")
			.version(MinecraftVersion.parse("1.21.11"))
			.protocolNumber(774)
			.nativeSessionType(Object.class)
			.build();

	@ParameterizedTest
	@ValueSource(strings = {"create", "destroy", "shutdown", "unqualified", ""})
	void rejectsReservedAndUnqualifiedOperations(String operation) {
		var registry = new WorkerCapabilityRegistry(CONTEXT, List.of(new Provider("reserved", (player, operations) -> {
			operations.register(operation, bytes -> bytes);
			return () -> { };
		})));

		assertThrows(IllegalArgumentException.class, () -> registry.bind(player(registry)));
	}

	@Test
	void rejectsDuplicatesRegistrationAfterBindingAndDuplicateProviders() {
		List<NativeOperations> scopes = new ArrayList<>();
		var registry = new WorkerCapabilityRegistry(CONTEXT, List.of(
				new Provider("first", (player, operations) -> {
					operations.register("example.action", bytes -> bytes);
					scopes.add(operations);
					return () -> { };
				}),
				new Provider("second", (player, operations) -> {
					operations.register("example.action", bytes -> bytes);
					return () -> { };
				})
		));

		assertThrows(IllegalStateException.class, () -> registry.bind(player(registry)));
		assertThrows(IllegalStateException.class, () -> scopes.getFirst().register("example.later", bytes -> bytes));
		assertThrows(IllegalStateException.class, () -> new WorkerCapabilityRegistry(CONTEXT, List.of(counter(), counter())));
	}

	@Test
	void unavailableAdaptersMakeTheExtensionUnavailableWithTheWorkersReason() {
		var registry = new WorkerCapabilityRegistry(CONTEXT, List.of(
				counter(),
				new Provider("adapted", (player, operations) -> {
					player.adapter(Runnable.class);
					return () -> { };
				})
		));

		try (var bindings = registry.bind(player(registry))) {
			assertEquals(1, count(bindings));
			assertEquals(Set.of("counter"), registry.capabilities());
			assertEquals(Map.of("adapted", "no mcprotocol segment selected for Minecraft 1.21.11 provides an adapter for java.lang.Runnable"),
					registry.unavailable());
		}
	}

	@Test
	void bindsIndependentStateForEachPlayer() {
		var registry = new WorkerCapabilityRegistry(CONTEXT, List.of(counter()));
		try (var first = registry.bind(player(registry)); var second = registry.bind(player(registry))) {
			assertEquals(1, count(first));
			assertEquals(2, count(first));
			assertEquals(1, count(second));
		}
	}

	@Test
	void linkageFailuresMakeTheExtensionUnavailableWithoutFailingThePlayer() {
		AtomicInteger attempts = new AtomicInteger();
		var registry = new WorkerCapabilityRegistry(CONTEXT, List.of(
				counter(),
				new Provider("linked", (player, operations) -> {
					attempts.incrementAndGet();
					throw new NoSuchMethodError("'void example.Packet.<init>(int)'");
				})
		));
		assertEquals(Set.of("counter", "linked"), registry.capabilities());

		try (var first = registry.bind(player(registry)); var second = registry.bind(player(registry))) {
			assertEquals(1, count(first));
			assertEquals(1, count(second));
			assertEquals(1, attempts.get());
			assertEquals(Set.of("counter"), registry.capabilities());
			assertEquals(Map.of("linked", "NoSuchMethodError: 'void example.Packet.<init>(int)'"), registry.unavailable());
			assertEquals(registry.unavailable(), registry.report().getUnavailable());
		}
	}

	@Test
	void linkageFailuresDropTheExtensionsOperationsAndReleaseItsNativeListeners() {
		List<String> listeners = new ArrayList<>();
		var registry = new WorkerCapabilityRegistry(CONTEXT, List.of(
				new Provider("partial", (player, operations) -> {
					operations.register("example.partial", bytes -> bytes);
					player.bindNativeSession(session -> {
						listeners.add("attached");
						return () -> listeners.add("detached");
					});
					throw new NoSuchMethodError("'void example.Packet.<init>(int)'");
				}),
				counter()
		));

		try (var player = player(registry); var bindings = registry.bind(player)) {
			player.connect();
			assertEquals(List.of(), listeners);
			assertEquals(1, count(bindings));
			IllegalArgumentException rejected = assertThrows(IllegalArgumentException.class,
					() -> bindings.execute("example.partial", WorkerMessageCodec.message(new byte[0])));
			assertTrue(rejected.getMessage().contains("example.partial"));
			assertEquals(Map.of("partial", "NoSuchMethodError: 'void example.Packet.<init>(int)'"), registry.unavailable());
			assertEquals(Set.of("counter"), registry.capabilities());
		}
		assertEquals(List.of(), listeners);
	}

	@Test
	void reportsCapabilitiesThatExtensionsCannotInstallInThisWorker() {
		var registry = new WorkerCapabilityRegistry(CONTEXT, List.of(new NativeWorkerProvider() {
			public String id() { return "partial"; }
			public NativeWorkerExtension<Object> create(NativeWorkerContext context) {
				return new NativeWorkerExtension<>() {
					public Set<String> capabilities() { return Set.of("installed"); }
					public Map<String, String> unavailable() { return Map.of("missing", "requires another session"); }
					public NativeBinding bind(NativePlayer<Object> player, NativeOperations operations) { return () -> { }; }
				};
			}
		}));

		assertEquals(Set.of("installed"), registry.capabilities());
		assertEquals(Map.of("missing", "requires another session"), registry.unavailable());
	}

	@Test
	void errorsDuringBindingAndCleanupReleaseEveryAcquiredBinding() {
		List<String> closed = new ArrayList<>();
		AssertionError original = new AssertionError("binding failed");
		LinkageError cleanup = new LinkageError("cleanup failed");
		var registry = new WorkerCapabilityRegistry(CONTEXT, List.of(
				new Provider("first", (player, operations) -> () -> closed.add("first")),
				new Provider("second", (player, operations) -> () -> { closed.add("second"); throw cleanup; }),
				new Provider("broken", (player, operations) -> { throw original; })
		));
		assertSame(original, assertThrows(AssertionError.class, () -> registry.bind(player(registry))));
		assertEquals(List.of("second", "first"), closed);
		assertArrayEquals(new Throwable[]{cleanup}, original.getSuppressed());
	}

	private int count(WorkerCapabilityRegistry.PlayerBindings bindings) {
		var result = bindings.execute("example.count", WorkerMessageCodec.message(new byte[0]));
		return Integer.parseInt(new String(new WorkerMessageCodec().messageBytes(result), StandardCharsets.UTF_8));
	}

	private Provider counter() {
		return new Provider("counter", (player, operations) -> {
			AtomicInteger count = new AtomicInteger();
			operations.register("example.count", ignored -> Integer.toString(count.incrementAndGet()).getBytes(StandardCharsets.UTF_8));
			return () -> { };
		});
	}

	private McProtocolPlayer player(WorkerCapabilityRegistry registry) {
		var options = WorkerPlayerOptions.builder()
				.profile(WorkerProfile.builder().name("Alice").uniqueId(UUID.randomUUID()).build())
				.connection(WorkerConnection.builder().host("localhost").port(25565).build())
				.build();
		return new McProtocolPlayer("player", options, new RecordingClient(),
				SegmentRoots.none(), new WorkerMessageWriter(new PrintStream(OutputStream.nullOutputStream())), registry);
	}

	@RequiredArgsConstructor
	private static final class Provider implements NativeWorkerProvider {
		private final String id;
		private final BiFunction<NativePlayer<Object>, NativeOperations, NativeBinding> binding;
		public String id() { return id; }
		public NativeWorkerExtension<Object> create(NativeWorkerContext context) {
			return new NativeWorkerExtension<>() {
				public Set<String> capabilities() { return Set.of(id); }
				public NativeBinding bind(NativePlayer<Object> player, NativeOperations operations) { return binding.apply(player, operations); }
			};
		}
	}
}
