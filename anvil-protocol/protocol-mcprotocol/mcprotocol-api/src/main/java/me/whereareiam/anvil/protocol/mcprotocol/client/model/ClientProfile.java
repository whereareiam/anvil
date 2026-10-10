package me.whereareiam.anvil.protocol.mcprotocol.client.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Profile a client sends in its login request.
 *
 * <pre>{@code
 * ClientProfile profile = ClientProfile.builder().name("Alice").uniqueId(uuid).build();
 * }</pre>
 */
@Value
@Builder
public class ClientProfile {
	/**
	 * Profile name sent in the login request.
	 */
	@NotNull String name;

	/**
	 * Profile identity sent in the login request.
	 */
	@NotNull UUID uniqueId;
}
