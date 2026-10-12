package me.whereareiam.anvil.protocol.worker.model.message;

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
	/**
	 * Access token the client reports the login to the session server with.
	 */
	@ToString.Exclude
	@NotNull String accessToken;
	/**
	 * Session server the login is reported to, or null for the library's default.
	 */
	@Nullable URI sessionServer;
}
