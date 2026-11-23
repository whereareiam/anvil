package me.whereareiam.anvil.agent.api;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * The JSON form of the agent protocol's payloads, which both ends of an agent connection share: the host's client
 * that sends requests and reads their responses, and the platform agent that reads the requests and answers them.
 *
 * <p>A payload is written as its fields name it. Anvil's values read through fluent accessors, which Jackson does not
 * take for getters, so the form sees each payload's fields and none of its getters, whichever accessors it has. An
 * immutable payload is read back through its Lombok {@code @Builder} with {@code @Jacksonized}.</p>
 *
 * <pre>{@code
 * ObjectMapper mapper = AgentJson.configure(new ObjectMapper());
 * JsonNode request = mapper.valueToTree(AgentCommandRequest.builder().command("say hello").build());
 * AgentCommandRequest read = mapper.treeToValue(request, AgentCommandRequest.class);
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AgentJson {
	/**
	 * Teaches a mapper the JSON form of the agent protocol's payloads.
	 *
	 * @param mapper the mapper, which this changes
	 * @return the same mapper
	 */
	public static @NotNull ObjectMapper configure(@NotNull ObjectMapper mapper) {
		return mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
				.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
				.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
	}
}
