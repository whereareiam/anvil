package me.whereareiam.anvil.protocol.java.api.packet.cookie;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The client's answer to a {@link ClientboundCookieRequest}, sent in the phase the request arrived in. Releases
 * before Minecraft 1.20.5 have no cookies.
 */
@Value
public class ServerboundCookieResponse implements ServerboundPacket {
	/**
	 * The requested key, a resource location with its namespace, such as {@code minecraft:ticket}.
	 */
	@NotNull String key;
	/**
	 * The payload the client holds under the key, or {@code null} when it holds no cookie under it.
	 */
	byte @Nullable [] payload;
}
