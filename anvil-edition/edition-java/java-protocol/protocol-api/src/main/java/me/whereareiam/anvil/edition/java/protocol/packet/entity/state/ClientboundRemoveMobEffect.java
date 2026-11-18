package me.whereareiam.anvil.edition.java.protocol.packet.entity.state;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server takes an effect away from an entity.
 */
@Value
@FieldNameConstants
public class ClientboundRemoveMobEffect implements ClientboundPacket {
	/**
	 * The entity's protocol ID.
	 */
	@NotNull EntityId entityId;
	/**
	 * The effect's key with its namespace, such as {@code minecraft:speed}.
	 */
	@NotNull String effect;
}
