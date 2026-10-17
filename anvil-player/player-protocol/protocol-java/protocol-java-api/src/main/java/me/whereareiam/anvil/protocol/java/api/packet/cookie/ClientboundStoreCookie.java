package me.whereareiam.anvil.protocol.java.api.packet.cookie;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A cookie the server stores on the client under a key, in the configuration or game phase, replacing one with the
 * same key. Releases before Minecraft 1.20.5 have no cookies.
 */
@Value
public class ClientboundStoreCookie implements ClientboundPacket {
	/**
	 * The cookie's key, a resource location with its namespace, such as {@code minecraft:ticket}.
	 */
	@NotNull String key;
	/**
	 * The cookie's payload, at most 5120 bytes.
	 */
	byte @NotNull [] payload;
}
