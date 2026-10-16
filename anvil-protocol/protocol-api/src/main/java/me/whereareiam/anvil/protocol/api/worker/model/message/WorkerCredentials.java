package me.whereareiam.anvil.protocol.api.worker.model.message;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * Online credentials of a worker player, as the library's authentication resolved them; they travel only through
 * private worker stdin.
 */
@Value
@Builder
@Jacksonized
public class WorkerCredentials {
	/**
	 * Access token the client signs in with, such as the one it reports the login to a session server with.
	 */
	@ToString.Exclude
	@NotNull String accessToken;
	/**
	 * Session server the login is reported to, or null for the library's default.
	 */
	@Nullable URI sessionServer;
	/**
	 * Further secrets the client signs in with, in the text form the library's authentication defined, or null.
	 */
	@ToString.Exclude
	@Nullable String libraryCredentials;
}
