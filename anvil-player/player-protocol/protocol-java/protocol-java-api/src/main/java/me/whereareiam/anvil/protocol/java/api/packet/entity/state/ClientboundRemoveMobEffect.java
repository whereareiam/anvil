package me.whereareiam.anvil.protocol.java.api.packet.entity.state;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server takes an effect away from an entity.
 */
@Value
public class ClientboundRemoveMobEffect implements ClientboundPacket {
	/**
	 * The entity's protocol ID.
	 */
	int entityId;
	/**
	 * The effect's key with its namespace, such as {@code minecraft:speed}.
	 */
	@NotNull String effect;
}
