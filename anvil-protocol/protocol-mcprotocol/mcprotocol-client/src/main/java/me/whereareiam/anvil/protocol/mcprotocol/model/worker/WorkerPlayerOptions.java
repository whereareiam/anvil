package me.whereareiam.anvil.protocol.mcprotocol.model.worker;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Resolved identity and game endpoint; credentials travel only through private worker stdin.
 */
@Value
@Builder
@Jacksonized
public class WorkerPlayerOptions {
	@NotNull String name;
	@NotNull UUID uuid;
	@NotNull String host;
	int port;
	@ToString.Exclude
	@Nullable String accessToken;
}
