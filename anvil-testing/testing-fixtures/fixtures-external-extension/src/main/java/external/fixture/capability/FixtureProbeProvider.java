package external.fixture.capability;

import external.fixture.agent.FixtureOperations;
import external.fixture.protocol.FixtureConnection;
import me.whereareiam.anvil.agent.api.transport.AgentDirectory;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Combines a backend-specific execution service with backend-independent agent operations.
 */
public final class FixtureProbeProvider implements PlayerCapabilityProvider<FixtureProbeProvider.Probe> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("external.fixture.probe").supportedProtocolId("fixture")
				.supportedProtocolId("fixture-observer").build();
	}

	@Override
	public @NotNull Class<Probe> capability() {
		return Probe.class;
	}

	@Override
	public @NotNull Probe create(@NotNull PlayerCapabilityContext context) {
		FixtureConnection connection = context.requireService(FixtureConnection.class);
		AgentDirectory agents = context.requireService(AgentDirectory.class);
		return new Probe() {
			@Override
			public String lastSent() {
				return connection.lastSent();
			}

			@Override
			public String echo(String process, String value) {
				return agents.require(process).request(FixtureOperations.ECHO, value);
			}
		};
	}

	public interface Probe extends PlayerCapability {
		String lastSent();
		String echo(String process, String value);
	}
}
