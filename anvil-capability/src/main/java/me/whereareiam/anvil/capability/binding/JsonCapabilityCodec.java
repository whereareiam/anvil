package me.whereareiam.anvil.capability.binding;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.function.Function;

/**
 * Serializes only explicitly declared immutable capability schemas; no payload-provided type names are resolved.
 */
public final class JsonCapabilityCodec {
	private final ObjectMapper mapper = new ObjectMapper().registerModule(new ParameterNamesModule(JsonCreator.Mode.PROPERTIES));

	/**
	 * Adapts a typed operation handler to encoded requests and responses using its declared schemas.
	 *
	 * @param operation request and response schemas
	 * @param handler typed operation implementation
	 * @return handler that decodes requests and validates and encodes responses
	 */
	public @NotNull <Q, R> Function<byte[], byte[]> handler(
			@NotNull ChannelOperation<Q, R> operation,
			@NotNull Function<Q, R> handler
	) {
		return encoded -> {
			Q request = decode(encoded, operation.getRequestType());
			R response = handler.apply(request);
			return encode(operation.getResponseType().cast(response));
		};
	}

	public byte @NotNull [] encode(@Nullable Object value) {
		try {
			return mapper.writeValueAsBytes(value);
		} catch (JsonProcessingException failure) {
			throw new IllegalArgumentException("Could not encode capability message");
		}
	}

	public @Nullable <T> T decode(byte @NotNull [] value, @NotNull Class<T> type) {
		if (type == Void.class) return null;
		try {
			T decoded = mapper.readerFor(type)
					.with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
					.with(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
					.readValue(value);

			if (decoded == null) throw new IllegalArgumentException("Invalid capability message");
			return decoded;
		} catch (IOException | IllegalArgumentException failure) {
			throw new IllegalArgumentException("Invalid capability message for " + type.getName());
		}
	}
}
