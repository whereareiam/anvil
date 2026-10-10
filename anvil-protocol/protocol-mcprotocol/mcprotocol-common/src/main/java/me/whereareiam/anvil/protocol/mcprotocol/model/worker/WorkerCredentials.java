package me.whereareiam.anvil.protocol.mcprotocol.model.worker;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * Online credentials of a worker player; they travel only through private worker stdin.
 */
@Value
@Builder
@Jacksonized
public class WorkerCredentials {
	@ToString.Exclude
	@NotNull String accessToken;
	@Nullable URI sessionServer;
}
