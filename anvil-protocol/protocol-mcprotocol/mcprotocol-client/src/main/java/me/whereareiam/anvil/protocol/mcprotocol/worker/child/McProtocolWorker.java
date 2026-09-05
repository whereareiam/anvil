package me.whereareiam.anvil.protocol.mcprotocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.protocol.adapter.api.binding.ProtocolWorkerBinding;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerRequest;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns worker players and sequential lifecycle dispatch; capability operations stay in their registry.
 */
@RequiredArgsConstructor
final class McProtocolWorker implements AutoCloseable {
	private final @NotNull WorkerCapabilityRegistry capabilities;
	private final @NotNull WorkerMessageWriter responses;
	private final @NotNull ProtocolWorkerBinding binding;

	private final WorkerMessageCodec codec = new WorkerMessageCodec();
	private final Map<String, WorkerPlayerContext> players = new LinkedHashMap<>();
	private boolean running = true;

	void run(@NotNull InputStream input) throws IOException {
		try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while (running && (line = reader.readLine()) != null)
				handle(codec.decodeRequest(line));
		}
	}

	private void handle(WorkerRequest request) {
		try {
			var control = WorkerControlOperation.find(request.getOperation());
			JsonNode result = control.isPresent()
					? execute(control.get(), request)
					: capabilities.execute(request.getOperation(), require(request.getPlayer()), request.getArguments());
			responses.success(request.getId(), result);
		} catch (Exception failure) {
			responses.failure(request.getId(), failure);
		}
	}

	private JsonNode execute(WorkerControlOperation operation, WorkerRequest request) {
		switch (operation) {
			case CREATE_PLAYER -> create(request);
			case DESTROY_PLAYER -> destroy(request.getPlayer());
			case SHUTDOWN -> running = false;
		}
		return JsonNodeFactory.instance.objectNode();
	}

	private void create(WorkerRequest request) {
		String id = requirePlayerId(request.getPlayer());
		if (players.containsKey(id)) throw new IllegalArgumentException("Player already exists: " + id);

		WorkerPlayerOptions options = codec.decodePayload(request.getArguments(), WorkerPlayerOptions.class);
		if (options.getPort() < 1 || options.getPort() > 65535) throw new IllegalArgumentException("Invalid worker player options");
		players.put(id, new WorkerPlayerContext(id, options, responses, capabilities, binding));
	}

	private void destroy(@Nullable String id) {
		WorkerPlayerContext removed = players.remove(requirePlayerId(id));
		if (removed != null)
			removed.close();
	}

	private WorkerPlayerContext require(@Nullable String id) {
		WorkerPlayerContext player = players.get(requirePlayerId(id));
		if (player == null)
			throw new IllegalArgumentException("Unknown worker player: " + id);
		return player;
	}

	private String requirePlayerId(@Nullable String id) {
		if (id == null || id.isBlank())
			throw new IllegalArgumentException("Worker operation requires a player ID");
		return id;
	}

	@Override
	public void close() {
		running = false;
		IllegalStateException failure = null;
		for (WorkerPlayerContext player : players.values()) {
			try {
				player.close();
			} catch (RuntimeException exception) {
				if (failure == null)
					failure = new IllegalStateException("Could not close all worker players");
				failure.addSuppressed(exception);
			}
		}
		players.clear();
		if (failure != null)
			throw failure;
	}
}
