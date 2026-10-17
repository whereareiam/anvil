package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server removes entities from the player's view.
 */
@Value
public class ClientboundRemoveEntities implements ClientboundPacket {
	/**
	 * The protocol IDs of the removed entities.
	 */
	@NotNull List<Integer> entityIds;
}
