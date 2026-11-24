package me.whereareiam.anvil.capability.network.agent;

import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.capability.network.agent.model.AgentChannelList;
import org.jetbrains.annotations.NotNull;

/**
 * The agent channel of the network capability: the operations its host provider requests and every platform side
 * answers, in the namespace the platform sides own.
 */
public final class NetworkOperations {
	/**
	 * The namespace the operations belong to, which the platform sides own.
	 */
	public static final @NotNull String NAMESPACE = "me.whereareiam.anvil.network";
	/**
	 * Lists the payload channels the server registered.
	 */
	public static final @NotNull AgentOperation<Void, AgentChannelList> CHANNELS = AgentOperation.<Void, AgentChannelList>builder()
			.name(NAMESPACE + ".channels").requestType(Void.class).responseType(AgentChannelList.class).build();

	private NetworkOperations() {
	}
}
