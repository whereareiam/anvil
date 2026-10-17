package me.whereareiam.anvil.protocol.mcprotocol.worker.transport;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerMessage;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerReady;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerResponse;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Map;
import java.util.Set;

/**
 * Writes framed worker responses and player events to the host's stdout channel.
 */
@RequiredArgsConstructor
public final class WorkerMessageWriter {
	private final @NotNull PrintStream output;
	private final WorkerMessageCodec codec = new WorkerMessageCodec();

	public void ready(
			@NotNull String release,
			int protocol,
			@NotNull Map<String, String> segments,
			@NotNull Set<String> capabilities,
			@NotNull Map<String, String> unavailable
	) {
		write(WorkerReady.builder()
				.release(release)
				.protocol(protocol)
				.segments(segments)
				.capabilities(capabilities)
				.unavailable(unavailable)
				.build());
	}

	public void event(@NotNull String player, @NotNull String event, @NotNull JsonNode payload) {
		write(WorkerEvent.builder().event(event).player(player).payload(payload).build());
	}

	public void success(long id, @NotNull JsonNode result) {
		write(WorkerResponse.builder().id(id).success(true).result(result).build());
	}

	public void failure(long id, @NotNull Throwable failure) {
		write(WorkerResponse.builder().id(id).success(false)
				.error(failure.getClass().getSimpleName() + ": " + failure.getMessage()).build());
	}

	private synchronized void write(WorkerMessage message) {
		output.println(codec.encodeMessage(message));
		if (output.checkError()) throw new IllegalStateException("Could not write worker response");
	}
}
