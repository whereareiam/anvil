package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.jetbrains.annotations.NotNull;

/**
 * The server swings an arm of an entity. Before Minecraft 26.3 the server swings it with an animation of the entity,
 * which every release reports as this packet.
 */
@Value
public class ClientboundSwingAnimation implements ClientboundPacket {
	/**
	 * The protocol ID of the entity whose arm swings.
	 */
	int entityId;
	/**
	 * The hand whose arm swings.
	 */
	@NotNull InteractionHand hand;
}
