package me.whereareiam.anvil.protocol.mcprotocol.client.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Client information a client sends after login.
 *
 * <pre>{@code
 * ClientSettings settings = ClientSettings.builder().locale("en_us").viewDistance(8).build();
 * }</pre>
 */
@Value
@Builder
public class ClientSettings {
	/**
	 * Locale reported in the client information, such as {@code en_us}.
	 */
	@NotNull String locale;

	/**
	 * View distance in chunks reported in the client information.
	 */
	int viewDistance;
}
