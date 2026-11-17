package me.whereareiam.anvil.protocol.api.channel;

import me.whereareiam.anvil.protocol.api.model.channel.EventDescriptor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
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
import java.util.function.Function;

/**
 * A player's channel for the tests of host capabilities, which the test plays the worker of: it {@link #answer
 * answers} the operations the capability requests, {@link #publish publishes} the events the worker would emit, and
 * scripts what happens while the capability waits. A wait runs the events {@link #meanwhile(Runnable) scheduled} for
 * it and then checks its condition once, so a test never sleeps: the condition holds, or the wait fails as a timed out
 * one does.
 *
 * <pre>{@code
 * TestProtocolChannel channel = new TestProtocolChannel(Set.of(WorldChannel.ID));
 * channel.answer(WorldChannel.CHUNK, chunk -> true);
 * World world = new WorldProvider().create(context(channel));
 *
 * channel.meanwhile(() -> channel.publish(WorldChannel.DIMENSION, change));
 * world.dimensions().entered("minecraft:the_nether");
 * }</pre>
 */
public final class TestProtocolChannel implements ProtocolChannel {
	private final Set<String> installed;
	private final Map<String, Function<Object, Object>> answers = new ConcurrentHashMap<>();
	private final Map<String, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();
	private final List<String> requests = new CopyOnWriteArrayList<>();
	private final List<String> subscriptions = new CopyOnWriteArrayList<>();
	private volatile Runnable meanwhile = () -> {
	};

	/**
	 * Creates a channel to a worker that installed the given capabilities.
	 *
	 * @param installed the IDs of the installed capabilities
	 */
	public TestProtocolChannel(@NotNull Set<String> installed) {
		this.installed = Set.copyOf(installed);
	}

	/**
	 * Answers every later request of an operation, as the worker's handler would.
	 *
	 * @param operation the operation
	 * @param handler computes the response from the request
	 * @param <Q> request type
	 * @param <R> response type
	 */
	@SuppressWarnings("unchecked")
	public <Q, R> void answer(@NotNull OperationDescriptor<Q, R> operation, @NotNull Function<Q, R> handler) {
		answers.put(operation.getId(), request -> handler.apply((Q) request));
	}

	/**
	 * Delivers an event to the capability's listeners of it, as the worker emitting it would.
	 *
	 * @param event the event
	 * @param payload its payload
	 * @param <E> payload type
	 */
	public <E> void publish(@NotNull EventDescriptor<E> event, @Nullable E payload) {
		for (Consumer<Object> listener : listeners.getOrDefault(event.getId(), List.of())) listener.accept(payload);
	}

	/**
	 * Schedules what happens while the capability's next wait waits, such as the events it waits for.
	 *
	 * @param events runs once, at the start of the next wait
	 */
	public void meanwhile(@NotNull Runnable events) {
		meanwhile = events;
	}

	/**
	 * Returns the IDs of the operations the capability requested, in order.
	 *
	 * @return the requested operation IDs
	 */
	public @NotNull List<String> requests() {
		return List.copyOf(requests);
	}

	/**
	 * Returns the IDs of the events the capability subscribed to, in order.
	 *
	 * @return the subscribed event IDs
	 */
	public @NotNull List<String> subscriptions() {
		return List.copyOf(subscriptions);
	}

	@Override
	public <Q, R> @Nullable R request(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
		requests.add(operation.getId());
		Function<Object, Object> handler = answers.get(operation.getId());
		if (handler == null)
			throw new IllegalStateException("The test's worker does not answer " + operation.getId());

		return operation.getResponseType().cast(handler.apply(request));
	}

	@Override
	@SuppressWarnings("unchecked")
	public <E> @NotNull Subscription subscribe(@NotNull EventDescriptor<E> event, @NotNull Consumer<E> listener) {
		subscriptions.add(event.getId());
		List<Consumer<Object>> subscribed = listeners.computeIfAbsent(event.getId(),
				ignored -> new CopyOnWriteArrayList<>());
		Consumer<Object> typed = payload -> listener.accept((E) payload);
		subscribed.add(typed);

		return () -> subscribed.remove(typed);
	}

	@Override
	public void await(@NotNull BooleanSupplier condition, @NotNull String description, @NotNull Duration timeout) {
		Runnable events = meanwhile;
		meanwhile = () -> {
		};
		events.run();
		if (!condition.getAsBoolean())
			throw new IllegalStateException("Player did not " + description);
	}

	@Override
	public @NotNull Set<String> installedCapabilities() {
		return installed;
	}
}
