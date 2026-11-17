package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server removes entities from the player's view.
 */
@Value
@FieldNameConstants
public class ClientboundRemoveEntities implements ClientboundPacket {
	/**
	 * The protocol IDs of the removed entities.
	 */
	@NotNull List<EntityId> entityIds;
}
