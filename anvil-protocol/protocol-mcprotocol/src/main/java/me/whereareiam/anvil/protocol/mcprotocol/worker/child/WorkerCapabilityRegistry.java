package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.protocol.api.worker.NativeBinding;
import me.whereareiam.anvil.protocol.api.worker.NativeOperations;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerExtension;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerProvider;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import org.geysermc.mcprotocollib.network.ClientSession;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Function;

/**
 * Selects protocol-native assembly providers and owns each player's opaque operation table.
 */
final class WorkerCapabilityRegistry {
	private final Map<String, NativeWorkerExtension<ClientSession>> extensions = new LinkedHashMap<>();
	private final Set<String> capabilities = new LinkedHashSet<>();

	static @NotNull WorkerCapabilityRegistry discover(int protocolNumber) {
		List<NativeWorkerProvider<?>> providers = new ArrayList<>();
		ServiceLoader.load(NativeWorkerProvider.class).forEach(providers::add);

		return new WorkerCapabilityRegistry(protocolNumber, providers);
	}

	WorkerCapabilityRegistry(int protocolNumber, @NotNull Collection<? extends NativeWorkerProvider<?>> candidates) {
		for (NativeWorkerProvider<?> candidate : candidates) {
			if (!candidate.backendId().equals("mcprotocol")) continue;
			if (candidate.backendType() != ClientSession.class)
				throw new IllegalArgumentException("Worker provider '" + candidate.id() + "' requires a different native context");

			@SuppressWarnings("unchecked")
			NativeWorkerProvider<ClientSession> provider = (NativeWorkerProvider<ClientSession>) candidate;
			NativeWorkerExtension<ClientSession> extension = provider.create(protocolNumber);
			if (extensions.putIfAbsent(provider.id(), extension) != null)
				throw new IllegalStateException("Duplicate native worker provider: " + provider.id());

			for (String id : extension.capabilities())
				if (!capabilities.add(id)) throw new IllegalStateException("Duplicate native capability: " + id);
		}
	}

	@NotNull PlayerBindings bind(@NotNull McProtocolPlayer player) {
		var bindings = new PlayerBindings();
		try {
			for (NativeWorkerExtension<ClientSession> extension : extensions.values())
				bindings.bindings.add(extension.bind(player, bindings));

			bindings.installing = false;
			return bindings;
		} catch (RuntimeException | Error failure) {
			try {
				bindings.close();
			} catch (RuntimeException | Error cleanup) {
				if (failure != cleanup) failure.addSuppressed(cleanup);
			}

			throw failure;
		}
	}

	@NotNull Set<String> capabilities() {
		return Set.copyOf(capabilities);
	}

	static final class PlayerBindings implements NativeOperations, AutoCloseable {
		private final WorkerMessageCodec codec = new WorkerMessageCodec();
		private final Map<String, Function<byte[], byte[]>> operations = new LinkedHashMap<>();
		private final List<NativeBinding> bindings = new ArrayList<>();
		private boolean installing = true;

		@Override
		public void register(@NotNull String id, @NotNull Function<byte[], byte[]> handler) {
			if (!installing) throw new IllegalStateException("Worker operations may only be registered while binding a player");
			if (WorkerControlOperation.find(id).isPresent()) throw new IllegalArgumentException("Reserved lifecycle operation: " + id);
			if (id.isBlank() || (!id.contains(".") && !id.contains(":"))) throw new IllegalArgumentException("Worker operation must be namespaced: " + id);
			if (operations.putIfAbsent(id, handler) != null) throw new IllegalStateException("Duplicate native operation: " + id);
		}

		@NotNull JsonNode execute(@NotNull String operation, @NotNull JsonNode arguments) {
			Function<byte[], byte[]> handler = operations.get(operation);
			if (handler == null) throw new IllegalArgumentException("Unknown protocol operation: " + operation);

			return WorkerMessageCodec.message(handler.apply(codec.messageBytes(arguments)));
		}

		@Override
		public void close() {
			Throwable failure = null;
			for (NativeBinding binding : bindings.reversed()) {
				try {
					binding.close();
				} catch (RuntimeException | Error cleanup) {
					if (failure == null) failure = cleanup;
					else if (cleanup != failure) failure.addSuppressed(cleanup);
				}
			}

			bindings.clear();
			operations.clear();
			installing = false;

			if (failure instanceof RuntimeException exception) throw exception;
			if (failure instanceof Error error) throw error;
		}
	}
}
