package me.whereareiam.anvil.integration.intellij.tooling.protocol;

import com.fasterxml.jackson.core.type.TypeReference;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.integration.intellij.model.SessionLogEntry;
import me.whereareiam.anvil.integration.intellij.tooling.process.ToolingConnection;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns one connection's handshake and pending requests, including terminal completion.
 */
@RequiredArgsConstructor
public final class ToolingClient {
	private final @NotNull ToolingConnection connection;
	private final @NotNull Executor executor;
	private final @NotNull Consumer<SessionSnapshot> snapshots;
	private final @NotNull Consumer<SessionLogEntry> output;
	private final @NotNull Consumer<Throwable> failures;
	private final @NotNull ToolingMessageCodec codec = new ToolingMessageCodec();
	private final @NotNull Map<String, Pending<?>> pending = new HashMap<>();
	private final @NotNull CompletableFuture<Void> ready = new CompletableFuture<>();

	private @Nullable Throwable closed;
	private @NotNull CompletableFuture<Void> writes = CompletableFuture.completedFuture(null);

	public @NotNull CompletableFuture<Void> ready() {
		return ready.copy();
	}

	public void receive(@NotNull String line) {
		try {
			switch (codec.read(line)) {
				case ToolingMessageCodec.Ready ignored -> ready.complete(null);
				case ToolingMessageCodec.Snapshot event -> snapshots.accept(event.value());
				case ToolingMessageCodec.Output event -> {
					var entry = event.value();
					output.accept(new SessionLogEntry(entry.getProcess(), entry.getExecutionId(),
							entry.getSequence(), entry.getText() + "\n", false));
				}
				case ToolingMessageCodec.Failure event -> throw new IOException(event.message());
				case ToolingMessageCodec.Response event -> complete(event);
				case ToolingMessageCodec.Queued ignored -> {}
			}
		} catch (Exception failure) {
			close(failure);
			failures.accept(failure);
		}
	}

	public void close(@NotNull Throwable failure) {
		List<Pending<?>> abandoned;
		synchronized (this) {
			if (closed != null) return;
			closed = failure;
			abandoned = List.copyOf(pending.values());
			pending.clear();
		}

		ready.completeExceptionally(failure);
		abandoned.forEach(request -> request.fail(failure));
	}

	/**
	 * Submits a declared operation. Submission completes when its frame has been written; the
	 * result completes after the correlated payload has been decoded with the declared schema.
	 */
	public <Q, R> @NotNull Exchange<R> request(@NotNull ToolingOperation<Q, R> operation, @Nullable Q payload) {
		Pending<R> request;
		try {
			String id = UUID.randomUUID().toString();
			request = new Pending<>(id, codec.write(id, operation, payload), operation.getResponseType());
		} catch (Exception failure) {
			return new Exchange<>(CompletableFuture.completedFuture(false), CompletableFuture.failedFuture(failure));
		}

		Throwable rejection;
		synchronized (this) {
			rejection = closed;
			if (rejection == null) {
				pending.put(request.id, request);
				writes = writes.handle((ignored, failure) -> null)
						.thenRunAsync(() -> write(request), executor);
				writes.exceptionally(failure -> {
					synchronized (this) {
						pending.remove(request.id);
					}
					request.fail(failure);
					return null;
				});
			}
		}
		if (rejection != null) request.fail(rejection);

		return new Exchange<>(request.submitted, request.result);
	}

	private <T> void write(Pending<T> request) {
		Throwable rejection;
		synchronized (this) {
			rejection = closed;
			if (rejection == null && !ready.isDone()) {
				rejection = new IllegalStateException(
						"Anvil is not ready for commands."
				);
			}
		}

		if (rejection != null) {
			reject(request, rejection);
			return;
		}

		try {
			connection.send(request.line);
			request.submitted.complete(true);
		} catch (IOException | RuntimeException failure) {
			reject(request, failure);
		}
	}

	private void reject(Pending<?> request, Throwable failure) {
		synchronized (this) {
			pending.remove(request.id);
		}
		request.fail(failure);
	}

	private void complete(ToolingMessageCodec.Response response) throws IOException {
		Pending<?> request;
		synchronized (this) {
			request = pending.remove(response.id());
		}

		if (request == null) return;
		request.complete(response);
	}

	/**
	 * Separates transport submission from the remote result.
	 */
	public record Exchange<T>(
			@NotNull CompletableFuture<Boolean> submitted,
			@NotNull CompletableFuture<T> result
	) { }

	@RequiredArgsConstructor
	private final class Pending<T> {
		private final @NotNull String id;
		private final @NotNull String line;
		private final @NotNull TypeReference<T> responseType;
		private final @NotNull CompletableFuture<Boolean> submitted = new CompletableFuture<>();
		private final @NotNull CompletableFuture<T> result = new CompletableFuture<>();

		private void complete(ToolingMessageCodec.Response response) throws IOException {
			if (response.error() != null) {
				result.completeExceptionally(new IllegalStateException(response.error()));
				return;
			}

			try {
				result.complete(codec.decode(response.result(), responseType));
			} catch (Exception failure) {
				result.completeExceptionally(failure);
				throw new IOException("Invalid tooling response for request " + id, failure);
			}
		}

		private void fail(Throwable failure) {
			submitted.complete(false);
			result.completeExceptionally(failure);
		}
	}
}
