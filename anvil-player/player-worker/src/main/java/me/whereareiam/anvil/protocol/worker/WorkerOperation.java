package me.whereareiam.anvil.protocol.worker;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * One operation a worker extension registered for a player: its descriptor and its typed handler. The library
 * decodes a request with the descriptor's request type, applies the operation and encodes the response with the
 * descriptor's response type, in whatever encoding its transport uses.
 *
 * <pre>{@code
 * WorkerOperation<?, ?> operation = bindings.operation(id);
 * return codec.execute(operation, request);
 * }</pre>
 *
 * @param <Q> request type
 * @param <R> response type
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class WorkerOperation<Q, R> {
	@Getter
	private final @NotNull OperationDescriptor<Q, R> descriptor;
	private final @NotNull Function<Q, R> handler;

	/**
	 * Applies the operation to a decoded request.
	 *
	 * @param request request value, or {@code null} for a {@link Void} request
	 * @return the handler's response
	 */
	public R apply(Q request) {
		return handler.apply(request);
	}
}
