package external.fixture.agent;

import me.whereareiam.anvil.agent.api.model.AgentOperation;

/**
 * Shared wire contract used by the host and platform halves of the external fixture.
 */
public final class FixtureOperations {
	public static final AgentOperation<String, String> ECHO = AgentOperation.<String, String>builder()
			.name("external.fixture.echo").requestType(String.class).responseType(String.class).build();
}
