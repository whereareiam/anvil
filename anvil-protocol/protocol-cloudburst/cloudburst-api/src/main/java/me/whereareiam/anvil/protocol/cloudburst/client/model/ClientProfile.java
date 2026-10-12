package me.whereareiam.anvil.protocol.cloudburst.client.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Profile a Bedrock client logs in as, or the one the server grants it after login. A profile of an Xbox account
 * carries the account's XUID, which a Bedrock client names itself by in the packets that carry its own XUID, such as
 * chat; an offline profile and a granted profile carry none.
 *
 * <pre>{@code
 * ClientProfile offline = ClientProfile.builder().name("Alice").uniqueId(uuid).build();
 * ClientProfile xbox = ClientProfile.builder().name("Alice").uniqueId(uuid).xuid("2535400000000000").build();
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

	/**
	 * XUID of the Xbox account the profile belongs to, or an empty string for an offline profile and for a profile the
	 * server granted.
	 */
	@Builder.Default
	@NotNull String xuid = "";
}
