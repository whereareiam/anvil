package me.whereareiam.anvil.edition.java.protocol.packet.overlay;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * A subtitle the server shows under the current title, or under the next one when no title is shown.
 */
@Value
@FieldNameConstants
public class ClientboundSetSubtitleText implements ClientboundPacket {
	/**
	 * The subtitle as plain text.
	 */
	@NotNull String text;
}
