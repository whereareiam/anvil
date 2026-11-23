package me.whereareiam.anvil.integration.intellij;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Writes tooling payloads in the JSON form the tooling runner sends them in: through their fields, because their
 * accessors are fluent, so a test emulating the runner hands the IDE what the runner would.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RunnerJson {
	/**
	 * Creates a mapper that reads and writes tooling payloads as the runner does.
	 *
	 * @return a new mapper
	 */
	public static ObjectMapper mapper() {
		return new ObjectMapper()
				.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
				.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
				.setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE);
	}
}
