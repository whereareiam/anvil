package me.whereareiam.anvil.capability.network.agent;

import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationRegistry;
import me.whereareiam.anvil.capability.network.agent.model.AgentChannel;
import me.whereareiam.anvil.capability.network.agent.model.AgentChannelList;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Answers the network capability's operations on one platform: each platform side extends it with how its platform's
 * network lists the channels it registered, read on the platform's own thread.
 */
public abstract class NetworkAgentOperations implements AgentOperationProvider {
	@Override
	public final @NotNull String id() {
		return NetworkOperations.NAMESPACE;
	}

	@Override
	public final void install(@NotNull PlatformAgent platform, @NotNull AgentOperationRegistry registry) {
		registry.register(NetworkOperations.CHANNELS, (agent, ignored) ->
				agent.call(() -> AgentChannelList.builder().channels(channels(platform)).build()));
	}

	/**
	 * Lists the payload channels the platform's network registered.
	 *
	 * @param platform the platform agent, whose services reach the platform's API
	 * @return the channels
	 */
	protected abstract @NotNull List<AgentChannel> channels(@NotNull PlatformAgent platform);
}
