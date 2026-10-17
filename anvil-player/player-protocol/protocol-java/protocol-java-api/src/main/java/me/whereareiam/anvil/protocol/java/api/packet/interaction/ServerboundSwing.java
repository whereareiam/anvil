package me.whereareiam.anvil.protocol.java.api.packet.interaction;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.InteractionHand;
import org.jetbrains.annotations.NotNull;

/**
 * The player swings an arm, as it does with every attack, item use and block use.
 */
@Value
public class ServerboundSwing implements ServerboundPacket {
	/**
	 * The hand whose arm swings.
	 */
	@NotNull InteractionHand hand;
}
