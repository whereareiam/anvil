package external.fixture.agent;

import me.whereareiam.anvil.agent.server.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

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
		var prefix = new AtomicReference<>("fixture");
		registry.register(FixtureOperations.ECHO, (platform, request) -> platform.call(() -> prefix.get() + ":" + request));
		registry.register(FixtureOperations.SET_PREFIX, (platform, request) -> {
			prefix.set(request);
			return null;
		});
		registry.register(FixtureOperations.GET_PREFIX, (platform, request) -> prefix.get());
	}
}
