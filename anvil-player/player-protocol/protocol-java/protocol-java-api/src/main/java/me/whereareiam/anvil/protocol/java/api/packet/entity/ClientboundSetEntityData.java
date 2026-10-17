package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityDataValue;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server changes data of an entity. The release decodes each value from the index the entity's type keeps it at;
 * values the protocol does not model are left out, so the list may be empty.
 */
@Value
public class ClientboundSetEntityData implements ClientboundPacket {
	/**
	 * The entity's protocol ID.
	 */
	int entityId;
	/**
	 * The changed values the protocol models, in the order the server sent them.
	 */
	@NotNull List<EntityDataValue> values;
}
