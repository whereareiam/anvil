package me.whereareiam.anvil.protocol.cloudburst.client.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Profile a Bedrock client logs in as, or the one the server grants it after login.
 *
 * <pre>{@code
 * ClientProfile profile = ClientProfile.builder().name("Alice").uniqueId(uuid).build();
 * }</pre>
 */
@Value
@Builder
public class ClientProfile {
	/**
	 * Display name of the profile.
	 */
	@NotNull String name;

	/**
	 * Profile identity.
	 */
	@NotNull UUID uniqueId;
}
