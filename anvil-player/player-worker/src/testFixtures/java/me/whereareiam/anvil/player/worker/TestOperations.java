package me.whereareiam.anvil.player.worker;

import me.whereareiam.anvil.player.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.player.api.worker.OperationRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The operations a worker extension registers while it binds, for its test to call as the host would: by descriptor,
 * with a request of the descriptor's request type. An operation ID is registered once, as a worker refuses a second
 * registration.
 *
 * <pre>{@code
 * TestOperations operations = new TestOperations();
 * extension.bind(player, operations);
 * CookieValue value = operations.call(CookiesChannel.GET, new CookieKey("example:session"));
 * }</pre>
 */
public final class TestOperations implements OperationRegistry {
	private final Map<String, WorkerOperation<?, ?>> operations = new LinkedHashMap<>();

	@Override
	public synchronized <Q, R> void register(
			@NotNull OperationDescriptor<Q, R> operation,
			@NotNull Function<Q, R> handler
	) {
		if (operations.putIfAbsent(operation.getId(), new WorkerOperation<>(operation, handler)) != null)
			throw new IllegalArgumentException("Operation already registered: " + operation.getId());
	}

	/**
	 * Returns the IDs of the registered operations, in the order they were registered.
	 *
	 * @return the operation IDs
	 */
	public synchronized @NotNull List<String> registered() {
		return List.copyOf(operations.keySet());
	}

	/**
	 * Calls a registered operation as the host would.
	 *
	 * @param operation the operation's descriptor, which must equal the registered one
	 * @param request the request, {@code null} for an operation that takes none
	 * @param <Q> request type
	 * @param <R> response type
	 * @return the operation's response, {@code null} only for a {@link Void} response
	 * @throws IllegalArgumentException when no operation of that ID is registered, or one with another schema
	 * @throws IllegalStateException when the operation answered {@code null} for a response other than {@link Void}
	 */
	public <Q, R> @Nullable R call(@NotNull OperationDescriptor<Q, R> operation, @Nullable Q request) {
		WorkerOperation<?, ?> registered;
		synchronized (this) {
			registered = operations.get(operation.getId());
		}
		if (registered == null)
			throw new IllegalArgumentException("Operation not registered: " + operation.getId());
		if (!registered.getDescriptor().equals(operation))
			throw new IllegalArgumentException("Operation " + operation.getId() + " is registered with another schema");

		@SuppressWarnings("unchecked")
		WorkerOperation<Q, R> typed = (WorkerOperation<Q, R>) registered;
		return typed.apply(request);
	}
}
