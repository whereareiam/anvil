package me.whereareiam.anvil.testing.server.extension;

import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.agent.api.transport.AgentDirectory;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Consumer-shaped host capability using the public agent-directory service.
 */
public final class FixtureAgentProbeProvider implements PlayerCapabilityProvider<FixtureAgentProbeProvider.Probe> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("anvil.testing.agent-probe").build();
	}

	@Override
	public @NotNull Class<Probe> capability() {
		return Probe.class;
	}

	@Override
	public @NotNull Probe create(@NotNull PlayerCapabilityContext context) {
		AgentDirectory agents = context.requireService(AgentDirectory.class);
		AgentOperation<String, String> operation = AgentOperation.<String, String>builder()
				.name("anvil.testing.fixture.echo").requestType(String.class).responseType(String.class).build();
		return (process, value) -> agents.require(process).request(operation, value);
	}

	public interface Probe extends PlayerCapability {
		String echo(String process, String value);
	}
}
