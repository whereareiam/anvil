package me.whereareiam.anvil.protocol.mcprotocol.model.worker;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolved login of one worker player, grouped as the client login it becomes: who logs in and where to. The
 * client settings are the worker's own. Credentials travel only through private worker stdin.
 */
@Value
@Builder
@Jacksonized
public class WorkerPlayerOptions {
	@NotNull WorkerProfile profile;
	@Nullable WorkerCredentials credentials;
	@NotNull WorkerConnection connection;
}
