package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.channel.ProtocolSubscription;
import me.whereareiam.anvil.protocol.api.exception.NativeAdapterUnavailableException;
import me.whereareiam.anvil.protocol.api.model.NativeWorkerContext;
import me.whereareiam.anvil.protocol.api.worker.NativeBinding;
import me.whereareiam.anvil.protocol.api.worker.NativeOperations;
import me.whereareiam.anvil.protocol.api.worker.NativePlayer;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerExtension;
import me.whereareiam.anvil.protocol.api.worker.NativeWorkerProvider;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerCapabilities;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Creates the worker's native extensions and owns each player's opaque operation table.
 *
 * <p>Each extension binds a player through its own scope: its operations are buffered and its native
 * listeners tracked until its binding returns. An extension whose binding fails to link against the loaded
 * release, or finds no adapter for its port, leaves nothing behind, loses its capabilities for this and later
 * players and does not fail the player.</p>
 */
final class WorkerCapabilityRegistry {
	private final Map<String, NativeWorkerExtension<Object>> extensions = new LinkedHashMap<>();
	// Linkage failure reasons of whole extensions, keyed by provider ID; they are not bound again.
	private final Map<String, String> unlinked = new LinkedHashMap<>();

	static @NotNull WorkerCapabilityRegistry discover(@NotNull NativeWorkerContext context) {
		List<NativeWorkerProvider> providers = new ArrayList<>();
		ServiceLoader.load(NativeWorkerProvider.class).forEach(providers::add);

		return new WorkerCapabilityRegistry(context, providers);
	}

	WorkerCapabilityRegistry(@NotNull NativeWorkerContext context, @NotNull Collection<? extends NativeWorkerProvider> providers) {
		Set<String> capabilities = new LinkedHashSet<>();
		for (NativeWorkerProvider provider : providers) {
			NativeWorkerExtension<Object> extension = provider.create(context);
			if (extensions.putIfAbsent(provider.id(), extension) != null)
				throw new IllegalStateException("Duplicate native worker provider: " + provider.id());

			for (String id : extension.capabilities())
				if (!capabilities.add(id)) throw new IllegalStateException("Duplicate native capability: " + id);
			for (String id : extension.unavailable().keySet())
				if (!capabilities.add(id)) throw new IllegalStateException("Duplicate native capability: " + id);
		}
	}

	@NotNull PlayerBindings bind(@NotNull NativePlayer<Object> player) {
		var bindings = new PlayerBindings();
		try {
			for (Map.Entry<String, NativeWorkerExtension<Object>> extension : extensions.entrySet()) {
				if (unlinked.containsKey(extension.getKey())) continue;

				String failure = bindings.bind(extension.getValue(), player);
				if (failure != null) unlinked.put(extension.getKey(), failure);
			}

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

	/**
	 * Returns the capabilities installed for new players.
	 *
	 * @return immutable capability IDs
	 */
	@NotNull Set<String> capabilities() {
		Set<String> installed = new LinkedHashSet<>();
		extensions.forEach((provider, extension) -> {
			if (!unlinked.containsKey(provider)) installed.addAll(extension.capabilities());
		});

		return Set.copyOf(installed);
	}

	/**
	 * Returns capabilities that cannot be installed, with the reason for each.
	 *
	 * @return immutable reasons keyed by capability ID
	 */
	@NotNull Map<String, String> unavailable() {
		Map<String, String> reasons = new LinkedHashMap<>();
		extensions.forEach((provider, extension) -> {
			reasons.putAll(extension.unavailable());
			String reason = unlinked.get(provider);
			if (reason != null) extension.capabilities().forEach(id -> reasons.putIfAbsent(id, reason));
		});

		return Map.copyOf(reasons);
	}

	/**
	 * Reports the capabilities bound for players created from now on, including the one just bound.
	 *
	 * @return installed and unavailable capabilities
	 */
	@NotNull WorkerPlayerCapabilities report() {
		return WorkerPlayerCapabilities.builder()
				.capabilities(capabilities())
				.unavailable(unavailable())
				.build();
	}

	private static String reason(Throwable failure) {
		if (failure instanceof NativeAdapterUnavailableException) return failure.getMessage();

		return failure.getClass().getSimpleName() + ": " + failure.getMessage();
	}

	private static void validate(String id) {
		if (WorkerControlOperation.find(id).isPresent()) throw new IllegalArgumentException("Reserved lifecycle operation: " + id);
		if (id.isBlank() || (!id.contains(".") && !id.contains(":"))) throw new IllegalArgumentException("Worker operation must be namespaced: " + id);
	}

	/**
	 * One player's operation table and extension bindings. Operations are registered only through each
	 * extension's scope while the player binds.
	 */
	static final class PlayerBindings implements AutoCloseable {
		private final WorkerMessageCodec codec = new WorkerMessageCodec();
		private final Map<String, Function<byte[], byte[]>> operations = new LinkedHashMap<>();
		private final List<NativeBinding> bindings = new ArrayList<>();
		private boolean installing = true;

		/**
		 * Binds one extension through its own scope, forwarding its operations only when the binding returns.
		 *
		 * @return the linkage or adapter failure reason, or null when the extension was bound
		 */
		private @Nullable String bind(NativeWorkerExtension<Object> extension, NativePlayer<Object> player) {
			ExtensionScope scope = new ExtensionScope(player);
			NativeBinding binding;
			try {
				binding = extension.bind(scope, scope);
			} catch (LinkageError | NativeAdapterUnavailableException failure) {
				scope.discard(null);
				return reason(failure);
			} catch (RuntimeException | Error failure) {
				scope.discard(failure);
				throw failure;
			}

			scope.open = false;
			bindings.add(binding);
			operations.putAll(scope.pending);
			return null;
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

		/**
		 * One extension's view of the player while it binds: operations are buffered and native listener
		 * registrations tracked, so a binding that fails to link can be released completely.
		 */
		@RequiredArgsConstructor
		private final class ExtensionScope implements NativePlayer<Object>, NativeOperations {
			private final NativePlayer<Object> player;
			private final Map<String, Function<byte[], byte[]>> pending = new LinkedHashMap<>();
			private final List<ProtocolSubscription> subscriptions = new ArrayList<>();
			private boolean open = true;

			@Override
			public void register(@NotNull String id, @NotNull Function<byte[], byte[]> handler) {
				if (!installing || !open) throw new IllegalStateException("Worker operations may only be registered while binding a player");
				validate(id);
				if (operations.containsKey(id) || pending.putIfAbsent(id, handler) != null)
					throw new IllegalStateException("Duplicate native operation: " + id);
			}

			private void discard(@Nullable Throwable failure) {
				open = false;
				pending.clear();
				for (ProtocolSubscription subscription : subscriptions.reversed()) {
					try {
						subscription.close();
					} catch (RuntimeException | Error cleanup) {
						if (failure != null && failure != cleanup) failure.addSuppressed(cleanup);
					}
				}
				subscriptions.clear();
			}

			@Override
			public @NotNull String name() {
				return player.name();
			}

			@Override
			public @NotNull UUID uniqueId() {
				return player.uniqueId();
			}

			@Override
			public void connect() {
				player.connect();
			}

			@Override
			public void disconnect() {
				player.disconnect();
			}

			@Override
			public void rejoin() {
				player.rejoin();
			}

			@Override
			public @NotNull Object nativeSession() {
				return player.nativeSession();
			}

			@Override
			public boolean isCurrentNativeSession(@NotNull Object nativeSession) {
				return player.isCurrentNativeSession(nativeSession);
			}

			@Override
			public @NotNull ProtocolSubscription bindNativeSession(@NotNull Function<Object, ProtocolSubscription> listener) {
				ProtocolSubscription subscription = player.bindNativeSession(listener);
				if (open) subscriptions.add(subscription);

				return subscription;
			}

			@Override
			public float yaw() {
				return player.yaw();
			}

			@Override
			public float pitch() {
				return player.pitch();
			}

			@Override
			public void view(float yaw, float pitch) {
				player.view(yaw, pitch);
			}

			@Override
			public void emit(@NotNull String event, byte @NotNull [] payload) {
				player.emit(event, payload);
			}

			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				return player.adapter(port);
			}
		}
	}
}
