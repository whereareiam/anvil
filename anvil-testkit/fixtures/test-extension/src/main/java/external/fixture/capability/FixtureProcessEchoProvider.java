package external.fixture.capability;

import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Contributes process-owned behavior through the agent-backed provider contract.
 */
public final class FixtureProcessEchoProvider implements AgentProcessCapabilityProvider<ProcessEcho> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("external.fixture.process-echo").build();
	}

	@Override
	public @NotNull Class<ProcessEcho> capability() {
		return ProcessEcho.class;
	}

	@Override
	public @NotNull ProcessEcho create(@NotNull AgentProcessCapabilityContext context) {
		var channel = context.channel();
		return new ProcessEcho() {
			@Override
			public @NotNull String echo(@NotNull String value) {
				return channel.request(FixtureChannels.ECHO, value);
			}

			@Override
			public void prefix(@NotNull String value) {
				channel.request(FixtureChannels.SET_PREFIX, value);
			}

			@Override
			public @NotNull String prefix() {
				return channel.request(FixtureChannels.GET_PREFIX, null);
			}
		};
	}
}
