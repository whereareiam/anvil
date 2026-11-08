package me.whereareiam.anvil.protocol.mcprotocol.model.worker;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolved login of one worker player, grouped as the client login it becomes: who logs in, in which game language,
 * and where to. The other client settings are the worker's own. Credentials travel only through private worker stdin.
 */
@Value
@Builder
@Jacksonized
public class WorkerPlayerOptions {
	@NotNull WorkerProfile profile;
	@Nullable WorkerCredentials credentials;
	@NotNull String language;
	@NotNull WorkerConnection connection;
}
