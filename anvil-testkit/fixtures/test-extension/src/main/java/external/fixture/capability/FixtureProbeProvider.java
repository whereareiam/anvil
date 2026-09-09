package external.fixture.capability;

import external.fixture.protocol.FixtureConnection;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Combines a backend-specific execution service with backend-independent agent operations.
 */
public final class FixtureProbeProvider implements ProtocolPlayerCapabilityProvider<FixtureProbeProvider.Probe> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id("external.fixture.probe")
				.requiredCapability(FixturePlayerLabelProvider.Label.class)
				.requiredCapability(PlayerAgentEcho.class)
				.build();
	}

	@Override
	public @NotNull Set<String> supportedProtocolIds() {
		return Set.of("fixture", "fixture-observer");
	}

	@Override
	public @NotNull Class<Probe> capability() {
		return Probe.class;
	}

	@Override
	public @NotNull Probe create(@NotNull ProtocolPlayerCapabilityContext context) {
		FixtureConnection connection = context.requireService(FixtureConnection.class);
		FixturePlayerLabelProvider.Label label = context.requireCapability(FixturePlayerLabelProvider.Label.class);
		PlayerAgentEcho agents = context.requireCapability(PlayerAgentEcho.class);
		return new Probe() {
			@Override
			public @NotNull String label() {
				return label.value();
			}

			@Override
			public @NotNull String agentLabel() {
				return agents.label();
			}

			@Override
			public String lastSent() {
				return connection.lastSent();
			}

			@Override
			public String echo(String process, String value) {
				return agents.echo(process, value);
			}
		};
	}

	public interface Probe extends PlayerCapability {
		@NotNull String label();
		@NotNull String agentLabel();
		String lastSent();
		String echo(String process, String value);
	}
}
