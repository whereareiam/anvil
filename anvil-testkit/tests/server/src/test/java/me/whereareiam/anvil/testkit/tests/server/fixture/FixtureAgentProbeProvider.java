package me.whereareiam.anvil.testkit.tests.server.fixture;

import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.process.AgentProcessCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import org.jetbrains.annotations.NotNull;

/**
 * Consumer-shaped process capability using its process's typed agent request channel.
 */
public final class FixtureAgentProbeProvider implements AgentProcessCapabilityProvider<FixtureAgentProbeProvider.Probe> {
	private static final ChannelOperation<String, String> ECHO = new ChannelOperation<>(
			"anvil.testing.fixture.echo", String.class, String.class
	);

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("anvil.testing.agent-probe").build();
	}

	@Override
	public @NotNull Class<Probe> capability() {
		return Probe.class;
	}

	@Override
	public @NotNull Probe create(@NotNull AgentProcessCapabilityContext context) {
		return value -> context.channel().request(ECHO, value);
	}

	public interface Probe extends ProcessCapability {
		String echo(String value);
	}
}
