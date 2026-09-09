package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.worker.NativeBinding;
import me.whereareiam.anvil.protocol.api.worker.NativeOperations;
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerExtension;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerProvider;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class WorkerCapabilityRegistryTest {
	@ParameterizedTest
	@ValueSource(strings = {"create", "destroy", "shutdown", "unqualified", ""})
	void rejectsReservedAndUnqualifiedOperations(String operation) {
		var bindings = new WorkerCapabilityRegistry.PlayerBindings();
		assertThrows(IllegalArgumentException.class, () -> bindings.register(operation, bytes -> bytes));
	}

	@Test
	void rejectsDuplicatesAndRegistrationAfterBinding() {
		var bindings = new WorkerCapabilityRegistry.PlayerBindings();
		bindings.register("example.action", bytes -> bytes);
		assertThrows(IllegalStateException.class, () -> bindings.register("example.action", bytes -> bytes));
		bindings.close();
		assertThrows(IllegalStateException.class, () -> bindings.register("example.action", bytes -> bytes));
		assertThrows(IllegalStateException.class, () -> new WorkerCapabilityRegistry(774, List.of(counter(), counter())));
	}

	@Test
	void bindsIndependentStateForEachPlayer() {
		var registry = new WorkerCapabilityRegistry(774, List.of(counter()));
		try (var first = registry.bind(player(registry)); var second = registry.bind(player(registry))) {
			assertEquals(1, count(first));
			assertEquals(2, count(first));
			assertEquals(1, count(second));
		}
	}

	@Test
	void rejectsWrongNativeContextBeforeBinding() {
		NativeWorkerProvider<String> provider = new NativeWorkerProvider<>() {
			public String id() { return "invalid"; }
			public String backendId() { return "mcprotocol"; }
			public Class<String> backendType() { return String.class; }
			public NativeWorkerExtension<String> create(int protocolNumber) { throw new AssertionError(); }
		};
		assertThrows(IllegalArgumentException.class, () -> new WorkerCapabilityRegistry(774, List.of(provider)));
	}

	@Test
	void errorsDuringBindingAndCleanupReleaseEveryAcquiredBinding() {
		List<String> closed = new ArrayList<>();
		AssertionError original = new AssertionError("binding failed");
		LinkageError cleanup = new LinkageError("cleanup failed");
		var registry = new WorkerCapabilityRegistry(774, List.of(
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
		return new McProtocolPlayer("player", "Alice", "localhost", 25565, UUID.randomUUID(), null,
				new WorkerMessageWriter(new PrintStream(OutputStream.nullOutputStream())), registry);
	}

	@RequiredArgsConstructor
	private static final class Provider implements NativeWorkerProvider<ClientSession> {
		private final String id;
		private final BiFunction<NativePlayer<ClientSession>, NativeOperations, NativeBinding> binding;
		public String id() { return id; }
		public String backendId() { return "mcprotocol"; }
		public Class<ClientSession> backendType() { return ClientSession.class; }
		public NativeWorkerExtension<ClientSession> create(int protocolNumber) {
			return new NativeWorkerExtension<>() {
				public Set<String> capabilities() { return Set.of(id); }
				public NativeBinding bind(NativePlayer<ClientSession> player, NativeOperations operations) { return binding.apply(player, operations); }
			};
		}
	}
}
