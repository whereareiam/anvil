package external.fixture.capability;

import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityContext;
import me.whereareiam.anvil.capability.agent.api.player.AgentPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Contributes player-owned behavior through the scenario's agent request channels.
 */
public final class FixturePlayerAgentEchoProvider implements AgentPlayerCapabilityProvider<PlayerAgentEcho> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("external.fixture.player-agent-echo")
				.requiredCapability(FixturePlayerLabelProvider.Label.class).build();
	}

	@Override
	public @NotNull Class<PlayerAgentEcho> capability() {
		return PlayerAgentEcho.class;
	}

	@Override
	public @NotNull PlayerAgentEcho create(@NotNull AgentPlayerCapabilityContext context) {
		FixturePlayerLabelProvider.Label label = context.requireCapability(FixturePlayerLabelProvider.Label.class);
		return new PlayerAgentEcho() {
			@Override
			public @NotNull String label() {
				return label.value();
			}

			@Override
			public @NotNull String echo(@NotNull String process, @NotNull String value) {
				return context.channel(process).request(FixtureChannels.ECHO, value);
			}
		};
	}
}
