package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
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
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.McProtocolClient;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerCapabilities;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Selects the worker's extensions and owns each player's operation table.
 *
 * <p>An extension is installed when it targets this library, or every library, and accepts the worker's native
 * session type. Each extension binds a player through its own scope: its operations are buffered and its native
 * listeners tracked until its binding returns. An extension whose session type is missing from the loaded release,
 * whose binding fails to link against the release, finds no adapter for its port, or fails its own service lookup
 * leaves nothing behind, is reported unavailable with the reason for this and later players, and does not fail the
 * player.</p>
 */
final class WorkerCapabilityRegistry {
	private final Map<String, WorkerExtension<Object>> extensions = new LinkedHashMap<>();
	private final Map<String, String> unavailable = new LinkedHashMap<>();
	private final WorkerMessageCodec codec = new WorkerMessageCodec();

	/**
	 * Discovers the extensions on the worker's class path. A provider class that cannot be loaded is skipped with a
	 * diagnostic on standard error naming the provider where the failure does, because its capability ID is unknown.
	 * A failure to read the provider configuration itself ends the discovery with the same diagnostic.
	 *
	 * @param nativeSessionType class of the worker's native sessions
	 * @return registry of the extensions selected for this worker
	 */
	static @NotNull WorkerCapabilityRegistry discover(@NotNull Class<?> nativeSessionType) {
		List<WorkerExtension<?>> discovered = new ArrayList<>();
		Iterator<ServiceLoader.Provider<WorkerExtension>> providers = ServiceLoader.load(WorkerExtension.class)
				.stream()
				.iterator();
		while (hasNext(providers)) {
			try {
				discovered.add(providers.next().get());
			} catch (ServiceConfigurationError | LinkageError failure) {
				warnSkipped(failure);
			}
		}

		return new WorkerCapabilityRegistry(nativeSessionType, discovered);
	}

	private static boolean hasNext(Iterator<?> providers) {
		try {
			return providers.hasNext();
		} catch (ServiceConfigurationError | LinkageError failure) {
			warnSkipped(failure);
			return false;
		}
	}

	private static void warnSkipped(Throwable failure) {
		System.err.println("[Anvil] Warning: skipped a worker extension that cannot be loaded: "
				+ loadFailure(failure));
	}

	/**
	 * Selects extensions from explicit candidates.
	 *
	 * @param nativeSessionType class of the worker's native sessions
	 * @param candidates extensions of any library
	 * @throws IllegalStateException when two candidates of this worker share a capability ID
	 */
	WorkerCapabilityRegistry(
			@NotNull Class<?> nativeSessionType,
			@NotNull Collection<? extends WorkerExtension<?>> candidates
	) {
		for (WorkerExtension<?> candidate : candidates) {
			boolean otherLibrary = candidate.libraryId()
					.filter(library -> !library.equals(McProtocolClient.LIBRARY_ID))
					.isPresent();
			if (otherLibrary) continue;
			if (extensions.containsKey(candidate.id()) || unavailable.containsKey(candidate.id()))
				throw new IllegalStateException("Duplicate worker capability: " + candidate.id());

			Class<?> required;
			try {
				required = candidate.nativeSessionType();
			} catch (LinkageError failure) {
				unavailable.put(candidate.id(), reason(failure));
				continue;
			}
			if (!required.isAssignableFrom(nativeSessionType)) {
				unavailable.put(candidate.id(), "requires native session " + required.getName() + ", but the "
						+ McProtocolClient.LIBRARY_ID + " worker provides " + nativeSessionType.getName());
				continue;
			}

			@SuppressWarnings("unchecked")
			WorkerExtension<Object> extension = (WorkerExtension<Object>) candidate;
			extensions.put(extension.id(), extension);
		}
	}

	/**
	 * Binds every installed extension to a player. An extension that is unavailable on this release is removed
	 * from the registry; any other failure closes the completed bindings and is rethrown.
	 *
	 * @param player the player's lifecycle, session access and event delivery
	 * @return the player's bindings and operation table
	 */
	synchronized @NotNull PlayerBindings bind(@NotNull PlayerBindingContext<Object> player) {
		var bindings = new PlayerBindings();
		try {
			for (WorkerExtension<Object> extension : List.copyOf(extensions.values())) {
				String failure = bindings.bind(extension, player);
				if (failure == null) continue;

				extensions.remove(extension.id());
				unavailable.put(extension.id(), failure);
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
	synchronized @NotNull Set<String> capabilities() {
		return Set.copyOf(extensions.keySet());
	}

	/**
	 * Returns capabilities that cannot be installed, with the reason for each.
	 *
	 * @return immutable reasons keyed by capability ID
	 */
	synchronized @NotNull Map<String, String> unavailable() {
		return Map.copyOf(unavailable);
	}

	/**
	 * Reports the capabilities bound for players created from now on, including the one just bound.
	 *
	 * @return installed and unavailable capabilities
	 */
	synchronized @NotNull WorkerPlayerCapabilities report() {
		return WorkerPlayerCapabilities.builder()
				.capabilities(capabilities())
				.unavailable(unavailable())
				.build();
	}

	private static String reason(Throwable failure) {
		if (failure instanceof ProtocolUnavailableException) return failure.getMessage();

		return failure.getClass().getSimpleName() + ": " + failure.getMessage();
	}

	private static String loadFailure(Throwable failure) {
		Throwable cause = failure;
		while (cause.getCause() != null) cause = cause.getCause();
		if (cause == failure) return reason(failure);

		// A service failure names the provider; its root cause names what the provider could not link against.
		return reason(failure) + " (" + reason(cause) + ")";
	}

	private static void validate(String id) {
		if (WorkerControlOperation.find(id).isPresent()) throw new IllegalArgumentException("Reserved lifecycle operation: " + id);
		if (id.isBlank() || (!id.contains(".") && !id.contains(":"))) throw new IllegalArgumentException("Worker operation must be namespaced: " + id);
	}

	/**
	 * One player's operation table and extension bindings. Operations are registered only through each
	 * extension's scope while the player binds.
	 */
	final class PlayerBindings implements AutoCloseable {
		private final Map<String, Function<JsonNode, JsonNode>> operations = new LinkedHashMap<>();
		private final List<WorkerBinding> bindings = new ArrayList<>();
		private boolean installing = true;

		/**
		 * Binds one extension through its own scope, forwarding its operations only when the binding returns.
		 *
		 * @return the linkage, service configuration or adapter failure reason, or null when the extension was bound
		 */
		private @Nullable String bind(WorkerExtension<Object> extension, PlayerBindingContext<Object> player) {
			ExtensionScope scope = new ExtensionScope(player);
			WorkerBinding binding;
			try {
				binding = extension.bind(scope, scope);
			} catch (LinkageError | ServiceConfigurationError | ProtocolUnavailableException failure) {
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

		/**
		 * Executes one registered operation.
		 *
		 * @param operation operation ID
		 * @param request request envelope
		 * @return response envelope
		 */
		@NotNull JsonNode execute(@NotNull String operation, @NotNull JsonNode request) {
			Function<JsonNode, JsonNode> handler = operations.get(operation);
			if (handler == null) throw new IllegalArgumentException("Unknown protocol operation: " + operation);

			return handler.apply(request);
		}

		@Override
		public void close() {
			Throwable failure = null;
			for (WorkerBinding binding : bindings.reversed()) {
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
		private final class ExtensionScope implements PlayerBindingContext<Object>, OperationRegistry {
			private final PlayerBindingContext<Object> player;
			private final Map<String, Function<JsonNode, JsonNode>> pending = new LinkedHashMap<>();
			private final List<Subscription> subscriptions = new ArrayList<>();
			private boolean open = true;

			@Override
			public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
				if (!installing || !open) throw new IllegalStateException("Worker operations may only be registered while binding a player");

				String id = operation.getId();
				validate(id);
				if (operations.containsKey(id) || pending.putIfAbsent(id, codec.handler(operation, handler)) != null)
					throw new IllegalStateException("Duplicate worker operation: " + id);
			}

			private void discard(@Nullable Throwable failure) {
				open = false;
				pending.clear();
				for (Subscription subscription : subscriptions.reversed()) {
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
			public @NotNull Subscription bindNativeSession(@NotNull Function<Object, Subscription> listener) {
				Subscription subscription = player.bindNativeSession(listener);
				if (open) subscriptions.add(subscription);

				return subscription;
			}

			@Override
			public @NotNull ViewRotation viewRotation() {
				return player.viewRotation();
			}

			@Override
			public void viewRotation(@NotNull ViewRotation rotation) {
				player.viewRotation(rotation);
			}

			@Override
			public <E> void emit(@NotNull EventDescriptor<E> event, @Nullable E payload) {
				player.emit(event, payload);
			}

			@Override
			public <P> @NotNull P protocol(@NotNull Class<P> type) {
				if (type != JavaProtocol.class) return player.protocol(type);

				return type.cast(new ScopedJavaProtocol(player.protocol(JavaProtocol.class)));
			}

			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				return player.adapter(port);
			}

			/**
			 * The player's protocol as the extension sees it while binding: its listeners are released with the scope
			 * when the binding fails.
			 */
			@RequiredArgsConstructor
			private final class ScopedJavaProtocol implements JavaProtocol {
				private final JavaProtocol protocol;

				@Override
				public <T extends ClientboundPacket> @NotNull Subscription listen(
						@NotNull Class<T> type,
						@NotNull Consumer<? super T> listener
				) {
					Subscription subscription = protocol.listen(type, listener);
					if (open) subscriptions.add(subscription);

					return subscription;
				}

				@Override
				public void send(@NotNull ServerboundPacket packet) {
					protocol.send(packet);
				}

				@Override
				public <T extends ServerboundPacket> @NotNull Subscription listenSent(
						@NotNull Class<T> type,
						@NotNull Consumer<? super T> listener
				) {
					Subscription subscription = protocol.listenSent(type, listener);
					if (open) subscriptions.add(subscription);

					return subscription;
				}

				@Override
				public boolean supports(@NotNull Class<? extends JavaPacket> type) {
					return protocol.supports(type);
				}
			}
		}
	}
}
