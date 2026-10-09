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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Selects the capability extensions of one native worker, coordinates typed registration, and owns
 * binding rollback.
 *
 * <p>An extension is installed when it targets the worker's library, or every library, and accepts the
 * worker's native session type. An extension whose session type is missing from the loaded library
 * release, that fails to link against the release while binding a player, whose adapter lookup fails
 * with {@link AdapterUnavailableException} (for example when no segment for the loaded release provides
 * its port), or whose own service lookup fails with a {@link ServiceConfigurationError} (for example an
 * external extension that loads its services itself) is reported unavailable with the reason; it is no
 * longer bound for later players and the failing player keeps its other capabilities.</p>
 */
public final class WorkerCapabilities {
	private final Map<String, WorkerExtension<Object>> extensions = new LinkedHashMap<>();
	private final Map<String, String> unavailable = new LinkedHashMap<>();

	/**
	 * Selects extensions from explicit candidates, primarily for embedding and contract tests.
	 *
	 * @param libraryId identifier of the library owning the worker
	 * @param nativeSessionType class of the worker's native sessions
	 * @param candidates discovered extensions of any library
	 * @throws IllegalStateException when two installed extensions share a capability ID
	 */
	public WorkerCapabilities(
			@NotNull String libraryId,
			@NotNull Class<?> nativeSessionType,
			@NotNull Collection<? extends WorkerExtension<?>> candidates
	) {
		for (WorkerExtension<?> candidate : candidates) {
			if (candidate.libraryId().isPresent() && !candidate.libraryId().get().equals(libraryId)) continue;
			if (extensions.containsKey(candidate.id()) || unavailable.containsKey(candidate.id()))
				throw new IllegalStateException("Duplicate native capability ID: " + candidate.id());

			Class<?> required;
			try {
				required = candidate.nativeSessionType();
			} catch (LinkageError failure) {
				unavailable.put(candidate.id(), reason(failure));
				continue;
			}
			if (!required.isAssignableFrom(nativeSessionType)) {
				unavailable.put(candidate.id(), "requires native session " + required.getName()
						+ ", but the " + libraryId + " worker provides " + nativeSessionType.getName());
				continue;
			}

			@SuppressWarnings("unchecked")
			WorkerExtension<Object> extension = (WorkerExtension<Object>) candidate;
			extensions.put(extension.id(), extension);
		}
	}

	/**
	 * Discovers extensions from the current worker class path. A provider class that cannot be loaded
	 * is skipped with a diagnostic on standard error naming the provider where the failure does, because
	 * its capability ID is unknown. Extensions that take their release-specific code from adapters load on
	 * every release, so a provider fails here only when its own wiring links against a library.
	 *
	 * @param libraryId identifier of the library owning the worker
	 * @param nativeSessionType class of the worker's native sessions
	 * @return extensions selected for this worker
	 */
	public static @NotNull WorkerCapabilities discover(@NotNull String libraryId, @NotNull Class<?> nativeSessionType) {
		List<WorkerExtension<?>> discovered = new ArrayList<>();
		Iterator<WorkerExtension> providers = ServiceLoader.load(WorkerExtension.class).iterator();
		while (true) {
			try {
				if (!providers.hasNext()) break;
				discovered.add(providers.next());
			} catch (ServiceConfigurationError | LinkageError failure) {
				System.err.println("[Anvil] Warning: skipped a worker extension that cannot be loaded: " + loadFailure(failure));
			}
		}

		return new WorkerCapabilities(libraryId, nativeSessionType, discovered);
	}

	/**
	 * Returns the capabilities currently installed for new players.
	 *
	 * @return immutable capability IDs
	 */
	public synchronized @NotNull Set<String> capabilities() {
		return Set.copyOf(extensions.keySet());
	}

	/**
	 * Returns capabilities that cannot be installed, with the reason for each.
	 *
	 * @return immutable reasons keyed by capability ID
	 */
	public synchronized @NotNull Map<String, String> unavailable() {
		return Map.copyOf(unavailable);
	}

	/**
	 * Installs one player's bindings into the caller's typed registration scope.
	 * Each extension's operations are forwarded only after its binding succeeds. A linkage failure, a service
	 * configuration failure or an unavailable adapter releases that extension's native listeners and marks its
	 * capability unavailable; any other failure closes completed bindings and is rethrown, and the caller must
	 * discard the corresponding dispatch scope so that partially registered operations cannot be invoked.
	 *
	 * @param player native lifecycle and session access for this player
	 * @param operations borrowed registry supplied by the native integration
	 * @return bindings owned until permanent player destruction
	 */
	public synchronized @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		PlayerBindings bindings = new PlayerBindings(operations);
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
				if (cleanup != failure) failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

	private static String reason(Throwable failure) {
		if (failure instanceof AdapterUnavailableException) return failure.getMessage();

		return failure.getClass().getSimpleName() + ": " + failure.getMessage();
	}

	private static String loadFailure(Throwable failure) {
		Throwable cause = failure;
		while (cause.getCause() != null) cause = cause.getCause();
		if (cause == failure) return reason(failure);

		// A service failure names the provider; its root cause names what the provider could not link against.
		return reason(failure) + " (" + reason(cause) + ")";
	}

	@RequiredArgsConstructor
	private static final class PlayerBindings implements WorkerBinding {
		private final OperationRegistry operations;
		private final Set<String> installed = new LinkedHashSet<>();
		private final List<WorkerBinding> bindings = new ArrayList<>();
		private boolean installing = true;

		/**
		 * Binds one extension through its own scope.
		 *
		 * @return the linkage, service configuration or adapter failure reason, or null when the extension was bound
		 */
		private @Nullable String bind(WorkerExtension<Object> extension, PlayerBindingContext<Object> player) {
			ExtensionScope scope = new ExtensionScope(player);
			WorkerBinding binding;
			try {
				binding = extension.bind(scope, scope);
			} catch (LinkageError | ServiceConfigurationError | AdapterUnavailableException failure) {
				scope.discard(null);
				return reason(failure);
			} catch (RuntimeException | Error failure) {
				scope.discard(failure);
				throw failure;
			}

			scope.open = false;
			bindings.add(binding);
			scope.forward();
			return null;
		}

		@Override
		public void close() {
			Throwable failure = null;
			for (WorkerBinding binding : bindings.reversed()) {
				try { binding.close(); } catch (RuntimeException | Error cleanup) {
					if (failure == null) failure = cleanup;
					else if (failure != cleanup) failure.addSuppressed(cleanup);
				}
			}

			bindings.clear();
			installed.clear();
			installing = false;

			if (failure instanceof RuntimeException exception) throw exception;
			if (failure instanceof Error error) throw error;
		}

		/**
		 * One extension's view of the player: operations are buffered and native listeners tracked until
		 * the extension's binding returns, so a failed binding leaves nothing behind.
		 */
		@RequiredArgsConstructor
		private final class ExtensionScope implements PlayerBindingContext<Object>, OperationRegistry {
			private final PlayerBindingContext<Object> player;
			private final List<Consumer<OperationRegistry>> pending = new ArrayList<>();
			private final List<String> registered = new ArrayList<>();
			private final List<Subscription> subscriptions = new ArrayList<>();
			private boolean open = true;

			@Override
			public <Q, R> void register(@NotNull ChannelOperation<Q, R> channelOperation, @NotNull Function<Q, R> handler) {
				if (!installing || !open) throw new IllegalStateException("Operations may only be registered while binding a player");

				String id = channelOperation.getId();
				if (id.isBlank() || (!id.contains(".") && !id.contains(":"))) throw new IllegalArgumentException("ChannelOperation must be namespaced: " + id);
				if (!installed.add(id)) throw new IllegalStateException("Duplicate native channelOperation: " + id);

				registered.add(id);
				pending.add(target -> target.register(channelOperation, handler));
			}

			private void forward() {
				for (Consumer<OperationRegistry> registration : pending) registration.accept(operations);
				pending.clear();
			}

			private void discard(@Nullable Throwable failure) {
				open = false;
				pending.clear();
				registered.forEach(installed::remove);
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
			public <E> void emit(@NotNull EventDescriptor<E> eventDescriptor, @Nullable E payload) {
				player.emit(eventDescriptor, payload);
			}

			@Override
			public <P> @NotNull P adapter(@NotNull Class<P> port) {
				return player.adapter(port);
			}
		}
	}
}
