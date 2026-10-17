package me.whereareiam.anvil.protocol.java.api.packet.inventory;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A container the server opens for the player, such as a chest. Its contents arrive in a later packet.
 */
@Value
public class ClientboundOpenScreen implements ClientboundPacket {
	/**
	 * ID of the opened container, which later packets and clicks address it by.
	 */
	int containerId;
	/**
	 * Vanilla ID of the container's menu type, such as {@code minecraft:generic_9x3}.
	 */
	@NotNull String menuType;
	/**
	 * The container's title as plain text.
	 */
	@NotNull String title;
}
