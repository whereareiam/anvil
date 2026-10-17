package me.whereareiam.anvil.protocol.mcprotocol.worker.transport;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import me.whereareiam.anvil.protocol.api.model.channel.OperationDescriptor;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerMessage;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerReady;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerRequest;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerResponse;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

/**
 * Owns JSON-lines framing, envelope validation and the typed player values on both sides of the private worker
 * channel. Decode failures never retain input text or Jackson exceptions, which can contain credentials.
 */
public final class WorkerMessageCodec {
	private static final String FRAME_PREFIX = "ANVIL:";
	private static final String VALUE = "value";
	private final ObjectMapper mapper = new ObjectMapper();
	// Player operation and event values: immutable classes read through their creator parameters.
	private final ObjectMapper values = new ObjectMapper()
			.registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES));

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
			if (ready.getProtocol() <= 0 || !value.path("release").isTextual() || ready.getRelease().isBlank()
					|| !value.path("segments").isObject()
					|| !value.path("capabilities").isArray()
					|| !value.path("unavailable").isObject())
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

	/**
	 * Wraps a player operation or event value in the object envelope the worker channel carries.
	 *
	 * @param value request, response or event value, or {@code null} for {@link Void}
	 * @return envelope holding the encoded value
	 */
	public @NotNull ObjectNode envelope(@Nullable Object value) {
		ObjectNode envelope = JsonNodeFactory.instance.objectNode();
		envelope.set(VALUE, values.valueToTree(value));
		return envelope;
	}

	/**
	 * Returns the envelope of a {@link Void} value, such as the payload of an event without one.
	 *
	 * @return envelope holding no value
	 */
	public static @NotNull ObjectNode emptyEnvelope() {
		return JsonNodeFactory.instance.objectNode().putNull(VALUE);
	}

	/**
	 * Reads a player operation or event value from its envelope, decoding only the declared schema.
	 *
	 * @param envelope envelope received from the other side
	 * @param type declared schema; {@link Void} reads {@code null}
	 * @return decoded value
	 * @throws IllegalArgumentException when the envelope or its value does not match the schema
	 */
	public @Nullable <T> T open(@NotNull JsonNode envelope, @NotNull Class<T> type) {
		if (type == Void.class) return null;

		JsonNode value = envelope.get(VALUE);
		if (value == null || value.isNull()) throw invalidValue(type);
		try {
			T decoded = values.readerFor(type)
					.with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
					.with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
					.readValue(value);
			if (decoded == null) throw invalidValue(type);

			return decoded;
		} catch (IOException | IllegalArgumentException failure) {
			throw invalidValue(type);
		}
	}

	/**
	 * Adapts a typed operation handler to envelopes: it opens the request with the declared request schema and
	 * checks the handler's result against the declared response schema before wrapping it.
	 *
	 * @param operation request and response schemas
	 * @param handler typed operation implementation
	 * @return handler of request envelopes returning response envelopes
	 */
	public @NotNull <Q, R> Function<JsonNode, JsonNode> handler(
			@NotNull OperationDescriptor<Q, R> operation,
			@NotNull Function<Q, R> handler
	) {
		return request -> {
			R response = handler.apply(open(request, operation.getRequestType()));
			return envelope(operation.getResponseType().cast(response));
		};
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

	private static IllegalArgumentException invalidMessage() {
		return new IllegalArgumentException("Invalid worker message");
	}

	private static IllegalArgumentException invalidValue(Class<?> type) {
		return new IllegalArgumentException("Invalid player message for " + type.getName());
	}
}
