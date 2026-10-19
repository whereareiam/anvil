package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server sets an entity's velocity, as it does for a player it knocked back or hurt. A vanilla client takes it as
 * the entity's delta movement, its own player's included.
 */
@Value
public class ClientboundSetEntityMotion implements ClientboundPacket {
	/**
	 * The protocol ID of the entity.
	 */
	int entityId;
	/**
	 * The entity's new velocity, in blocks per tick.
	 */
	@NotNull Vec3d movement;
}
