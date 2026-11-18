package me.whereareiam.anvil.edition.java.protocol.packet.entity.state;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server sends attributes of an entity, such as the player's movement speed or jump strength, each replacing what
 * the client knew of that attribute. Attributes the packet leaves out keep their values.
 */
@Value
@FieldNameConstants
public class ClientboundUpdateAttributes implements ClientboundPacket {
	/**
	 * The entity's protocol ID.
	 */
	@NotNull EntityId entityId;
	/**
	 * The attributes the server sent, in its order.
	 */
	@NotNull List<AttributeSnapshot> attributes;
}
