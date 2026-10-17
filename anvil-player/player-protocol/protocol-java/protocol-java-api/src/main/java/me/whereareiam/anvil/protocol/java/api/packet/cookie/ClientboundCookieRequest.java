package me.whereareiam.anvil.protocol.java.api.packet.cookie;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server's request for the cookie the client holds under a key, in the login, configuration or game phase. The
 * client answers every request with {@link ServerboundCookieResponse}. Releases before Minecraft 1.20.5 have no
 * cookies.
 */
@Value
public class ClientboundCookieRequest implements ClientboundPacket {
	/**
	 * The requested key, a resource location with its namespace, such as {@code minecraft:ticket}.
	 */
	@NotNull String key;
}
