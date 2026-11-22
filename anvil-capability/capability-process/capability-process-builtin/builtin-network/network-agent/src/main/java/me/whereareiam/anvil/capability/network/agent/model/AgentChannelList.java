package me.whereareiam.anvil.capability.network.agent.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The payload channels a server registered.
 */
@Value
@Builder
@Jacksonized
public class AgentChannelList {
	/**
	 * The channels, in no particular order.
	 */
	@NotNull @Singular List<AgentChannel> channels;
}
