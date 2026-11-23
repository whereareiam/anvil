package me.whereareiam.anvil.tooling.api;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * The JSON form of the tooling protocol's payloads, which both peers of a {@link ToolingSession} connection share: the
 * runner that writes frames and reads requests, and the editor that writes requests and reads frames.
 *
 * <p>A payload is written as its fields name it. The payloads read through fluent accessors, which Jackson does not
 * take for getters, so the form sees each payload's fields and none of its getters. Immutable payloads are read back
 * through their Lombok builders' Jackson binding metadata. Each peer's codec keeps its own framing, headers, reading
 * strictness and version checks on top of this form.</p>
 *
 * <pre>{@code
 * ObjectMapper mapper = ToolingJson.configure(new ObjectMapper());
 * String catalog = mapper.writeValueAsString(session.scenarios());
 * }</pre>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ToolingJson {
	/**
	 * Teaches a mapper the JSON form of the tooling protocol's payloads.
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
