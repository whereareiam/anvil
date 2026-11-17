package me.whereareiam.anvil.protocol.java.api.packet.entity.state;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.entity.state.AttributeSnapshot;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server sends attributes of an entity, such as the player's movement speed or jump strength, each replacing what
 * the client knew of that attribute. Attributes the packet leaves out keep their values.
 */
@Value
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
