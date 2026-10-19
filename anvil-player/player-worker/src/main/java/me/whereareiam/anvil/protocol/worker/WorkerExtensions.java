package me.whereareiam.anvil.protocol.worker;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
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
import java.util.function.Function;

/**
 * The worker extensions one library's worker runs, and each player's bindings of them. A library creates it once
 * per worker and binds every player it creates; it then reports {@link #capabilities()} and {@link #unavailable()} to
 * the host, which composes only the capabilities installed.
 *
 * <p>An extension is installed when it targets this library, or every library, and accepts the worker's native
 * session type. Each extension binds a player through its own scope: its operations are buffered and its listeners
 * tracked until its binding returns. An extension whose session type is missing from the loaded release, whose
 * binding fails to link, whose protocol or adapter the player lacks, or whose own service lookup fails leaves
 * nothing behind and is reported unavailable with the reason, for this and every later player, without failing the
 * player.</p>
 *
 * <pre>{@code
 * WorkerExtensions extensions = WorkerExtensions.discover(LIBRARY_ID, client.sessionType(), Set.of("create"));
 * WorkerExtensions.PlayerBindings bindings = extensions.bind(player, protocols);
 * }</pre>
 */
public final class WorkerExtensions {
	private final String libraryId;
	private final Set<String> reservedOperations;
	private final Map<String, WorkerExtension<Object>> extensions = new LinkedHashMap<>();
	private final Map<String, String> unavailable = new LinkedHashMap<>();

	/**
	 * Discovers the extensions on the worker's class path. A provider class that cannot be loaded is skipped with a
	 * diagnostic on standard error naming the provider where the failure does, because its capability ID is unknown.
	 * A failure to read the provider configuration itself ends the discovery with the same diagnostic.
	 *
	 * @param libraryId the library whose worker runs the extensions
	 * @param nativeSessionType class of the worker's native sessions
	 * @param reservedOperations operation IDs the library's own transport uses, which no extension may register
	 * @return the extensions selected for this worker
	 * @throws IllegalStateException when two selected extensions share a capability ID
	 */
	public static @NotNull WorkerExtensions discover(
			@NotNull String libraryId,
			@NotNull Class<?> nativeSessionType,
			@NotNull Set<String> reservedOperations
	) {
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

		return new WorkerExtensions(libraryId, nativeSessionType, reservedOperations, discovered);
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
	 * @param libraryId the library whose worker runs the extensions
	 * @param nativeSessionType class of the worker's native sessions
	 * @param reservedOperations operation IDs the library's own transport uses, which no extension may register
	 * @param candidates extensions of any library
	 * @throws IllegalStateException when two candidates of this worker share a capability ID
	 */
	public WorkerExtensions(
			@NotNull String libraryId,
			@NotNull Class<?> nativeSessionType,
			@NotNull Set<String> reservedOperations,
			@NotNull Collection<? extends WorkerExtension<?>> candidates
	) {
		this.libraryId = libraryId;
		this.reservedOperations = Set.copyOf(reservedOperations);
		for (WorkerExtension<?> candidate : candidates) {
			boolean otherLibrary = candidate.libraryId().filter(library -> !library.equals(libraryId)).isPresent();
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
						+ libraryId + " worker provides " + nativeSessionType.getName());
				continue;
			}

			@SuppressWarnings("unchecked")
			WorkerExtension<Object> extension = (WorkerExtension<Object>) candidate;
			extensions.put(extension.id(), extension);
		}
	}

	/**
	 * Binds every installed extension to a player. An extension that is unavailable to the player is removed from
	 * the installed extensions; any other failure closes the completed bindings and is rethrown.
	 *
	 * @param player the player's lifecycle, sessions, events and adapters
	 * @param protocols the protocols the player speaks, whose scoped views each extension receives
	 * @return the player's bindings and operations
	 */
	public synchronized @NotNull PlayerBindings bind(
			@NotNull PlayerBindingContext<Object> player,
			@NotNull WorkerProtocols protocols
	) {
		var bindings = new PlayerBindings();
		try {
			for (WorkerExtension<Object> extension : List.copyOf(extensions.values())) {
				String failure = bindings.bind(extension, player, protocols);
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
	public synchronized @NotNull Set<String> capabilities() {
		return Set.copyOf(extensions.keySet());
	}

	/**
	 * Returns the capabilities that cannot be installed, with the reason for each.
	 *
	 * @return immutable reasons keyed by capability ID
	 */
	public synchronized @NotNull Map<String, String> unavailable() {
		return Map.copyOf(unavailable);
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

	private void validate(String id) {
		if (reservedOperations.contains(id)) throw new IllegalArgumentException("Reserved lifecycle operation: " + id);
		if (id.isBlank() || (!id.contains(".") && !id.contains(":")))
			throw new IllegalArgumentException("Worker operation must be namespaced: " + id);
	}

	/**
	 * One player's operations and extension bindings. Operations are registered only through each extension's scope
	 * while the player binds.
	 */
	public final class PlayerBindings implements AutoCloseable {
		private final Map<String, WorkerOperation<?, ?>> operations = new LinkedHashMap<>();
		private final List<WorkerBinding> bindings = new ArrayList<>();
		private boolean installing = true;

		private PlayerBindings() {
		}

		/**
		 * Binds one extension through its own scope, keeping its operations only when the binding returns.
		 *
		 * @return the linkage, service configuration or protocol failure reason, or null when the extension was bound
		 */
		private @Nullable String bind(
				WorkerExtension<Object> extension,
				PlayerBindingContext<Object> player,
				WorkerProtocols protocols
		) {
			ExtensionScope scope = new ExtensionScope(player, protocols);
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
		 * Returns an operation an extension registered for the player.
		 *
		 * @param id operation ID
		 * @return the operation with its descriptor
		 * @throws IllegalArgumentException when no extension registered the operation
		 */
		public @NotNull WorkerOperation<?, ?> operation(@NotNull String id) {
			WorkerOperation<?, ?> operation = operations.get(id);
			if (operation == null) throw new IllegalArgumentException("Unknown protocol operation: " + id);

			return operation;
		}

		/**
		 * Closes every binding in reverse order, preserving the first failure and suppressing later ones.
		 */
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
		 * One extension's view of the player while it binds: operations are buffered and listener registrations
		 * tracked, so a binding that fails can be released completely.
		 */
		@RequiredArgsConstructor
		private final class ExtensionScope implements PlayerBindingContext<Object>, OperationRegistry {
			private final PlayerBindingContext<Object> player;
			private final WorkerProtocols protocols;
			private final Map<String, WorkerOperation<?, ?>> pending = new LinkedHashMap<>();
			private final List<Subscription> subscriptions = new ArrayList<>();
			private boolean open = true;

			@Override
			public <Q, R> void register(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
				if (!installing || !open)
					throw new IllegalStateException("Worker operations may only be registered while binding a player");

				String id = operation.getId();
				validate(id);
				boolean duplicate = operations.containsKey(id)
						|| pending.putIfAbsent(id, new WorkerOperation<>(operation, handler)) != null;
				if (duplicate) throw new IllegalStateException("Duplicate worker operation: " + id);
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

			private void track(Subscription subscription) {
				if (open) subscriptions.add(subscription);
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
				track(subscription);

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
				return protocols.protocol(type, this::track);
			}

			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				return player.adapter(port);
			}

			@Override
			public @NotNull MinecraftVersion minecraftVersion() {
				return player.minecraftVersion();
			}
		}
	}
}
