package me.whereareiam.anvil.player.worker;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.player.api.model.channel.OperationDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
	 * Applies the operation to a decoded request and checks the handler's answer against the response schema.
	 *
	 * @param request request value, or {@code null} for a {@link Void} request
	 * @return the handler's response, {@code null} only for a {@link Void} response
	 * @throws IllegalStateException when the handler answered {@code null} for a response other than {@link Void}
	 */
	public @Nullable R apply(@Nullable Q request) {
		return descriptor.response(handler.apply(request));
	}
}
