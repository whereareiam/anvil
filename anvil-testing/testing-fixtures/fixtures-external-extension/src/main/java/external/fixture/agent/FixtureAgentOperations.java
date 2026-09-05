package external.fixture.agent;

import me.whereareiam.anvil.agent.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.api.operation.AgentOperationRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Platform extension compiled only against public Anvil contracts.
 */
public final class FixtureAgentOperations implements AgentOperationProvider {
	@Override
	public @NotNull String id() {
		return "external.fixture";
	}

	@Override
	public void install(@NotNull AgentOperationRegistry registry) {
		registry.register(FixtureOperations.ECHO, (platform, request) -> platform.call(() -> "fixture:" + request));
	}
}
