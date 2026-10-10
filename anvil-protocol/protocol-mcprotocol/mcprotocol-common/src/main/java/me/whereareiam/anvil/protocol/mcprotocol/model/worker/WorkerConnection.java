package me.whereareiam.anvil.protocol.mcprotocol.model.worker;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Game listener of a worker player, the host its handshake announces and the address literal it binds to.
 */
@Value
@Builder
@Jacksonized
public class WorkerConnection {
	@NotNull String host;
	int port;
	@Nullable String virtualHost;
	@Nullable String sourceAddress;
}
