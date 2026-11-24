package me.whereareiam.anvil.player.library.mcprotocol.model.worker;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Resolved login of one worker player, grouped as the client login it becomes: who logs in, as which Minecraft version
 * of the worker's release and which client, in which game language, and where to. The other client settings are the worker's own.
 * Credentials travel only through private worker stdin.
 */
@Value
@Builder
@Jacksonized
public class WorkerPlayerOptions {
	@NotNull WorkerProfile profile;
	@Nullable WorkerCredentials credentials;
	@NotNull String minecraftVersion;
	@NotNull String language;
	@NotNull WorkerClient client;
	@NotNull WorkerConnection connection;
}
