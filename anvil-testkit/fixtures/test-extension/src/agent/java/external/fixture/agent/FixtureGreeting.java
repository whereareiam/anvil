package external.fixture.agent;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable structured payload, bound by Jackson through the builder that {@code @Jacksonized} declares, as the agent
 * operation guide recommends for custom models.
 */
@Value
@Builder
@Jacksonized
public class FixtureGreeting {
	/**
	 * Name the greeting addresses.
	 */
	@NotNull String name;
	/**
	 * Number of times the greeting has passed through a handler.
	 */
	int count;
}
