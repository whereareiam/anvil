package me.whereareiam.anvil.protocol.mcprotocol.worker.transport;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerMessage;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerReady;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerRequest;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerResponse;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Owns JSON-lines framing and envelope validation on both sides of the private worker channel.
 * Decode failures never retain input text or Jackson exceptions, which can contain credentials.
 */
public final class WorkerMessageCodec {
	private static final String FRAME_PREFIX = "ANVIL:";
	private final ObjectMapper mapper = new ObjectMapper();

	public @NotNull String encodeRequest(@NotNull WorkerRequest request) {
		return encode(request);
	}

	public @NotNull WorkerRequest decodeRequest(@NotNull String line) {
		WorkerRequest request = decode(readObject(line), WorkerRequest.class);
		if (request.getId() <= 0 || request.getOperation().isBlank() || !request.getArguments().isObject())
			throw invalidMessage();
		return request;
	}

	public @NotNull String encodeMessage(@NotNull WorkerMessage message) {
		return FRAME_PREFIX + encode(message);
	}

	public @NotNull Optional<WorkerMessage> decodeMessage(@NotNull String line) {
		if (!line.startsWith(FRAME_PREFIX)) return Optional.empty();

		JsonNode value = readObject(line.substring(FRAME_PREFIX.length()));
		if (value.has("id")) {
			WorkerResponse response = decode(value, WorkerResponse.class);
			if (response.getId() <= 0 || !value.path("success").isBoolean()
					|| (response.isSuccess() ? response.getResult() == null : response.getError() == null))
				throw invalidMessage();
			return Optional.of(response);
		}

		if (WorkerReady.EVENT.equals(value.path("event").asText())) {
			WorkerReady ready = decode(value, WorkerReady.class);
			if (ready.getProtocol() <= 0 || !value.path("capabilities").isArray())
				throw invalidMessage();
			return Optional.of(ready);
		}

		WorkerEvent event = decode(value, WorkerEvent.class);
		if (event.getEvent().isBlank()) throw invalidMessage();

		return Optional.of(event);
	}

	public @NotNull JsonNode payload(@NotNull Object value) {
		return mapper.valueToTree(value);
	}

	public @NotNull <T> T decodePayload(@NotNull JsonNode value, @NotNull Class<T> type) {
		return decode(value, type);
	}

	private String encode(Object value) {
		try {
			return mapper.writeValueAsString(value);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("Could not encode worker message");
		}
	}

	private JsonNode readObject(String line) {
		try {
			JsonNode value = mapper.readTree(line);
			if (value == null || !value.isObject()) throw invalidMessage();

			return value;
		} catch (JsonProcessingException exception) {
			throw invalidMessage();
		}
	}

	private <T> T decode(JsonNode value, Class<T> type) {
		try {
			T decoded = mapper.treeToValue(value, type);
			if (decoded == null) throw invalidMessage();

			return decoded;
		} catch (JsonProcessingException | IllegalArgumentException exception) {
			throw invalidMessage();
		}
	}

	private IllegalArgumentException invalidMessage() {
		return new IllegalArgumentException("Invalid worker message");
	}
}
