package external.sample;

import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationRegistry;
import org.jetbrains.annotations.NotNull;

public final class EchoOperationProvider implements AgentOperationProvider {
	@Override
	public @NotNull String id() {
		return "external.sample";
	}

	@Override
	public void install(@NotNull AgentOperationRegistry registry) {
		registry.register(AgentOperation.<String, String>builder().name("external.sample.echo")
				.requestType(String.class).responseType(String.class).build(), (platform, request) -> "echo:" + request);
	}
}
