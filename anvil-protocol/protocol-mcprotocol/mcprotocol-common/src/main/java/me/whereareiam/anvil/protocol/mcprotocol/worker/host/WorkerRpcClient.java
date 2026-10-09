package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerRequest;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerResponse;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Correlates requests and responses over private stdin. A terminal transport failure rejects both
 * outstanding and future requests; process lifecycle belongs to the owning worker process.
 */
final class WorkerRpcClient {
	private final BufferedWriter writer;
	private final WorkerMessageCodec codec = new WorkerMessageCodec();
	private final Map<Long, CompletableFuture<JsonNode>> pending = new HashMap<>();
	private long nextId;
	private @Nullable Throwable failure;

	WorkerRpcClient(@NotNull OutputStream output) {
		writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
	}

	@NotNull JsonNode request(
			@NotNull String operation,
			@Nullable String player,
			@NotNull JsonNode arguments,
			@NotNull Duration timeout
	) {
		long id;
		CompletableFuture<JsonNode> response = new CompletableFuture<>();
		synchronized (this) {
			if (failure != null) throw new IllegalStateException("Worker request channel is closed", failure);

			id = ++nextId;
			pending.put(id, response);
		}

		try {
			WorkerRequest request = WorkerRequest.builder()
					.id(id).operation(operation).player(player).arguments(arguments).build();
			synchronized (writer) {
				writer.write(codec.encodeRequest(request));
				writer.newLine();
				writer.flush();
			}

			return response.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		} catch (IOException exception) {
			fail(exception);
			throw new IllegalStateException("Could not send protocol operation '" + operation + "'", exception);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted during protocol operation '" + operation + "'", exception);
		} catch (ExecutionException | TimeoutException exception) {
			throw new IllegalStateException("Protocol operation '" + operation + "' failed", exception);
		} finally {
			synchronized (this) {
				pending.remove(id);
			}
		}
	}

	synchronized void accept(@NotNull WorkerResponse response) {
		CompletableFuture<JsonNode> future = pending.remove(response.getId());
		if (future == null) return;
		if (response.isSuccess()) {
			future.complete(response.getResult());
			return;
		}

		future.completeExceptionally(new IllegalStateException(response.getError()));
	}

	synchronized void fail(@NotNull Throwable cause) {
		if (failure != null) return;

		failure = cause;
		pending.values().forEach(response -> response.completeExceptionally(cause));
		pending.clear();
	}
}
