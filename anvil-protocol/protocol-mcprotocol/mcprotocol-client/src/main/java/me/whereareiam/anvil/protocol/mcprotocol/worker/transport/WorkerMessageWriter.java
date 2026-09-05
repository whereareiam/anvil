package me.whereareiam.anvil.protocol.mcprotocol.worker.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerMessage;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerReady;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerResponse;
import org.jetbrains.annotations.NotNull;

import java.io.PrintStream;
import java.util.Set;

/**
 * Writes framed worker responses and player events to the host's stdout channel.
 */
@RequiredArgsConstructor
public final class WorkerMessageWriter {
	private final @NotNull PrintStream output;
	private final WorkerMessageCodec codec = new WorkerMessageCodec();

	public void ready(int protocol, @NotNull Set<String> capabilities) {
		write(WorkerReady.builder().protocol(protocol).capabilities(capabilities).build());
	}

	public void event(@NotNull String player, @NotNull String event, @NotNull ObjectNode payload) {
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
