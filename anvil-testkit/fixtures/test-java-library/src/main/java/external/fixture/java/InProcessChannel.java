package external.fixture.java;

import me.whereareiam.anvil.protocol.api.channel.ProtocolChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.worker.WorkerExtensions;
import me.whereareiam.anvil.protocol.worker.WorkerOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The host's side of one in-process player's channel, which calls the worker side in the same JVM: a request calls the
 * operation the player's extensions registered, and the events the player emits reach the host's listeners directly.
 */
final class InProcessChannel implements ProtocolChannel, AutoCloseable {
	private static final Duration POLL_INTERVAL = Duration.ofMillis(10);

	private final JavaFixturePlayer player;
	private final WorkerExtensions extensions;
	private final Map<String, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

	InProcessChannel(@NotNull JavaFixturePlayer player, @NotNull WorkerExtensions extensions) {
		this.player = player;
		this.extensions = extensions;
	}

	@Override
	public <Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
		if (player.destroyed()) throw new IllegalStateException("Player '" + player.name() + "' has been destroyed");

		WorkerOperation<?, ?> registered = player.operation(operation.getId());
		if (!registered.getDescriptor().equals(operation))
			throw new IllegalArgumentException("Operation " + operation.getId() + " is registered with another schema");

		@SuppressWarnings("unchecked")
		WorkerOperation<Q, R> typed = (WorkerOperation<Q, R>) registered;
		return typed.apply(request);
	}

	@Override
	public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> event, @NotNull Consumer<E> listener) {
		Consumer<Object> typed = payload -> listener.accept(event.getPayloadType().cast(payload));
		List<Consumer<Object>> subscribed = listeners.computeIfAbsent(event.getId(), ignored -> new CopyOnWriteArrayList<>());
		subscribed.add(typed);

		return () -> subscribed.remove(typed);
	}

	@Override
	public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (!condition.getAsBoolean()) {
			if (System.nanoTime() >= deadline)
				throw new IllegalStateException("Player '" + player.name() + "' did not " + description + " within "
						+ timeout);

			try {
				Thread.sleep(POLL_INTERVAL);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while waiting for player '" + player.name() + "'", exception);
			}
		}
	}

	@Override
	public @NotNull Set<String> installedCapabilities() {
		return extensions.capabilities();
	}

	@Override
	public @NotNull Map<String, String> unavailableCapabilities() {
		return extensions.unavailable();
	}

	/**
	 * Hands an event the player emitted to the host's listeners of it.
	 */
	<E> void publish(@NotNull EventDescriptor<E> event, @Nullable E payload) {
		for (Consumer<Object> listener : listeners.getOrDefault(event.getId(), List.of())) listener.accept(payload);
	}

	/**
	 * Forgets every listener, as the player is destroyed.
	 */
	@Override
	public void close() {
		listeners.clear();
	}
}
