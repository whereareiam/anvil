package me.whereareiam.anvil.integration.intellij.tooling.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;

import java.io.IOException;
import java.util.Objects;

import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.LogEvent;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;
import me.whereareiam.anvil.tooling.api.model.ToolingOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Maps protocol envelopes and shared tooling payloads at the JSON boundary.
 */
final class ToolingMessageCodec {
	private final @NotNull ObjectMapper json = JsonMapper.builder()
			.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
			.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
			.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
			.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
			.addMixIn(LogEvent.LogEventBuilder.class, EventPayload.class)
			.build();

	ToolingMessageCodec() {
		json.coercionConfigFor(LogicalType.Textual)
				.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
	}

	@NotNull Message read(@NotNull String line) throws IOException {
		JsonNode message = json.readTree(line);
		if (message == null || !message.isObject()) throw new IOException("Tooling message must be an object.");

		Header header = json.treeToValue(message, Header.class);
		return switch (header.type()) {
			case READY -> {
				Ready ready = json.treeToValue(message, Ready.class);
				if (ready.protocolVersion() != ToolingSession.PROTOCOL_VERSION)
					throw new IOException("Unsupported Anvil tooling protocol. Update the project and IDE plugin together.");
				yield ready;
			}
			case SNAPSHOT -> json.treeToValue(message, Snapshot.class);
			case RESPONSE -> json.treeToValue(message, Response.class);
			case ERROR -> json.treeToValue(message, Failure.class);
			case QUEUED -> json.treeToValue(message, Queued.class);
			case LOG -> new Output(json.treeToValue(message, LogEvent.class));
		};
	}

	<Q, R> @NotNull String write(
			@NotNull String id,
			@NotNull ToolingOperation<Q, R> operation,
			@Nullable Q payload
	) throws IOException {
		if (payload == null && operation.getRequestType() != Void.class)
			throw new IllegalArgumentException("Missing request for tooling operation '" + operation.getName() + "'");

		Q request = operation.getRequestType().cast(payload);
		return json.writeValueAsString(new Outgoing<>(id, operation.getName(), request));
	}

	<R> @NotNull R decode(@NotNull JsonNode payload, @NotNull TypeReference<R> responseType) throws IOException {
		return json.readerFor(responseType).readValue(payload);
	}

	private static void requireText(@Nullable String value, String field) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing tooling field: " + field);
	}

	private enum Kind {
		@JsonProperty("ready") READY,
		@JsonProperty("snapshot") SNAPSHOT,
		@JsonProperty("response") RESPONSE,
		@JsonProperty("error") ERROR,
		@JsonProperty("queued") QUEUED,
		@JsonProperty("log") LOG
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Header(@JsonProperty(required = true) Kind type) {
		private Header {
			Objects.requireNonNull(type, "Missing tooling message type");
		}
	}

	@JsonIgnoreProperties("type")
	private abstract static class EventPayload {}

	@JsonIgnoreProperties("type")
	sealed interface Message {}

	record Ready(@JsonProperty(required = true) Integer protocolVersion) implements Message {
		Ready {
			Objects.requireNonNull(protocolVersion, "Missing protocolVersion");
		}
	}

	record Snapshot(@JsonProperty(value = "snapshot", required = true) SessionSnapshot value) implements Message {
		Snapshot {
			Objects.requireNonNull(value, "Missing session snapshot");
		}
	}

	record Output(@NotNull LogEvent value) implements Message {}

	record Failure(@JsonProperty(value = "error", required = true) String message) implements Message {
		Failure {
			requireText(message, "error");
		}
	}

	record Queued(@JsonProperty(required = true) String requestId) implements Message {
		Queued {
			requireText(requestId, "requestId");
		}
	}

	record Response(
			@JsonProperty(required = true) String id,
			@JsonProperty(required = true) Boolean success,
			@Nullable JsonNode result,
			@Nullable String error
	) implements Message {
		Response {
			requireText(id, "id");
			Objects.requireNonNull(success, "Missing response success flag");
			if (!success) requireText(error, "error");
			if (success && (result == null || result.isNull()))
				throw new IllegalArgumentException("Successful tooling response requires a result");
			if (success && error != null)
				throw new IllegalArgumentException("Successful tooling response cannot contain an error");
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	private record Outgoing<Q>(
			@NotNull String id,
			@NotNull String operation,
			@JsonUnwrapped @Nullable Q payload
	) {}
}
