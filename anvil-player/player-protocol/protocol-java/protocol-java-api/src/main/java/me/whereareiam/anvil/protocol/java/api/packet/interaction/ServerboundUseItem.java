package me.whereareiam.anvil.protocol.java.api.packet.interaction;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.jetbrains.annotations.NotNull;

/**
 * The player uses the item held in a hand without targeting a block or an entity.
 */
@Value
@Builder
public class ServerboundUseItem implements ServerboundPacket {
	/**
	 * The hand holding the item.
	 */
	@NotNull InteractionHand hand;
	/**
	 * The player's block-change sequence number, which the server acknowledges; releases before Minecraft 1.19 do not
	 * send it.
	 */
	int sequence;
	/**
	 * The horizontal view direction in degrees; releases before Minecraft 1.21 do not send it.
	 */
	float yaw;
	/**
	 * The vertical view direction in degrees; releases before Minecraft 1.21 do not send it.
	 */
	float pitch;
}
