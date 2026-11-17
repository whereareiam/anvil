package me.whereareiam.anvil.protocol.java.api.packet.screen;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The times of the current and the following titles, in ticks. A negative time leaves that time unchanged.
 */
@Value
@FieldNameConstants
public class ClientboundSetTitlesAnimation implements ClientboundPacket {
	/**
	 * Ticks a title takes to fade in.
	 */
	int fadeIn;
	/**
	 * Ticks a title stays fully visible.
	 */
	int stay;
	/**
	 * Ticks a title takes to fade out.
	 */
	int fadeOut;
}
