package me.whereareiam.anvil.capability.network.agent.model;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A payload channel a server registered: its id, version, phase, direction (null for both) and whether a client may
 * lack it.
 */
@Value
@Builder
@Jacksonized
public class AgentChannel {
	@NotNull String id;
	@NotNull String version;
	@NotNull AgentChannelPhase phase;
	@Nullable AgentChannelFlow flow;
	boolean optional;
}
