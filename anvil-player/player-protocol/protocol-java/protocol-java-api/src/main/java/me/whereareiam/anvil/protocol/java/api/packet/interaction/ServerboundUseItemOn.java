package me.whereareiam.anvil.protocol.java.api.packet.interaction;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.interaction.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.Direction;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.jetbrains.annotations.NotNull;

/**
 * The player uses the item held in a hand on a face of a block.
 */
@Value
@Builder
public class ServerboundUseItemOn implements ServerboundPacket {
	/**
	 * The hand holding the item.
	 */
	@NotNull InteractionHand hand;
	/**
	 * The targeted block.
	 */
	@NotNull BlockPosition position;
	/**
	 * The targeted face of the block.
	 */
	@NotNull Direction face;
	/**
	 * The east-west offset of the targeted point from the block's corner, from zero through one.
	 */
	float cursorX;
	/**
	 * The vertical offset of the targeted point from the block's corner, from zero through one.
	 */
	float cursorY;
	/**
	 * The north-south offset of the targeted point from the block's corner, from zero through one.
	 */
	float cursorZ;
	/**
	 * Whether the player's head is inside the targeted block.
	 */
	boolean insideBlock;
	/**
	 * Whether the targeted point lies on the world border; releases before Minecraft 1.21.2 do not send it.
	 */
	boolean worldBorderHit;
	/**
	 * The player's block-change sequence number, which the server acknowledges; releases before Minecraft 1.19 do not
	 * send it.
	 */
	int sequence;
}
