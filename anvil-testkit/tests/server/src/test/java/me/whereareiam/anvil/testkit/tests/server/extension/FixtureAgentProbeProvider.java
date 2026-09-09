package me.whereareiam.anvil.testkit.tests.server.extension;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import org.jetbrains.annotations.NotNull;

/**
 * Consumer-shaped player capability using typed agent request channels.
 */
public final class FixtureAgentProbeProvider implements AgentPlayerCapabilityProvider<FixtureAgentProbeProvider.Probe> {
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
	public @NotNull Probe create(@NotNull AgentPlayerCapabilityContext context) {
		return (process, value) -> context.channel(process).request(ECHO, value);
	}

	public interface Probe extends PlayerCapability {
		String echo(String process, String value);
	}
}
