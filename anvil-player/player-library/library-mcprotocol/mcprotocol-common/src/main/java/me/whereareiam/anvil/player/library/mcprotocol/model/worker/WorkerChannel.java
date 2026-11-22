package me.whereareiam.anvil.player.library.mcprotocol.model.worker;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import me.whereareiam.anvil.api.type.network.ChannelFlow;
import me.whereareiam.anvil.api.type.network.ChannelPhase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Payload channel a worker player claims: its id, version, phase, direction (null for both) and whether it is
 * optional.
 */
@Value
@Builder
@Jacksonized
public class WorkerChannel {
	@NotNull String id;
	@NotNull String version;
	@NotNull ChannelPhase phase;
	@Nullable ChannelFlow flow;
	boolean optional;
}
