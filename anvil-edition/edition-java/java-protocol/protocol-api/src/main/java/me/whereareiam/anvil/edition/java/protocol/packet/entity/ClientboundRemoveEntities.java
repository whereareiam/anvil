package me.whereareiam.anvil.edition.java.protocol.packet.entity;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
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
