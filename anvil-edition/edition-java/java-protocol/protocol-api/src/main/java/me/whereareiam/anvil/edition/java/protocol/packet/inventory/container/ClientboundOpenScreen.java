package me.whereareiam.anvil.edition.java.protocol.packet.inventory.container;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.player.api.model.text.Text;
import org.jetbrains.annotations.NotNull;

/**
 * A container the server opens for the player, such as a chest. Its contents arrive in a later packet.
 */
@Value
@FieldNameConstants
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
	 * The container's title, as the text the server sent.
	 */
	@NotNull Text title;
}
