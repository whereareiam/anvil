package me.whereareiam.anvil.tooling.api.model.scenario;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.Nullable;

/**
 * Selects a scenario and an optional initial process. A definition or scenario identity must be
 * supplied; the runner prefers definition when both are present.
 */
@Value
@Builder
@Jacksonized
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScenarioLaunchRequest {
	/**
	 * Fully qualified definition identity, or null when using the scenario-name fallback.
	 */
	@Nullable String definition;

	/**
	 * Scenario name retained for protocol-7 callers that select by name instead of definition.
	 */
	@Nullable String scenario;

	/**
	 * Initial process name, or null to start the complete scenario and its setup.
	 */
	@Nullable String target;

	/**
	 * Whether to start the target process, or every server and proxy when there is no target, with a JDWP agent.
	 * The agent listens on a free loopback port and prints it to the process console as
	 * {@code Listening for transport dt_socket at address: <port>}, again after each restart.
	 */
	boolean debug;
}
