package me.whereareiam.anvil.runner.protocol;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Decodes transport headers and binds operation payloads before the runner invokes the session.
 */
final class ToolingRequestReader {
	private final @NotNull ObjectMapper json = JsonMapper.builder()
			.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
			.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
			.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
			.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
			.build();

	ToolingRequestReader() {
		json.coercionConfigFor(LogicalType.Textual)
				.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
	}

	@NotNull Request read(@NotNull String line) throws IOException {
		if (line.length() > 65536) throw new IOException("Request is too large");
		JsonNode payload = json.readTree(line);
		if (payload == null || !payload.isObject()) throw new IOException("Tooling request must be an object");
		Header header = json.treeToValue(payload, Header.class);
		return new Request(header.id(), header.operation(), payload);
	}

	<Q> @Nullable Q decode(@NotNull Request request, @NotNull Class<Q> requestType) throws IOException {
		if (requestType == Void.class) return null;

		return json.readerFor(requestType)
				.without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.readValue(request.payload());
	}

	private static void requireText(@Nullable String value, String field) {
		if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing request field: " + field);
	}

	record Request(@NotNull String id, @NotNull String operation, @NotNull JsonNode payload) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Header(@JsonProperty(required = true) String id, @JsonProperty(required = true) String operation) {
		private Header {
			requireText(id, "id");
			requireText(operation, "operation");
		}
	}
}
