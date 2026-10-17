package me.whereareiam.anvil.protocol.mcprotocol.client.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Profile a client sends in its login request, or the one the server grants it in its login success.
 *
 * <pre>{@code
 * ClientProfile profile = ClientProfile.builder().name("Alice").uniqueId(uuid).build();
 * }</pre>
 */
@Value
@Builder
public class ClientProfile {
	/**
	 * Profile name.
	 */
	@NotNull String name;

	/**
	 * Profile identity.
	 */
	@NotNull UUID uniqueId;
}
