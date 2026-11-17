package me.whereareiam.anvil.protocol.java.api.packet.dialog;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * Closes the dialog the player has open, in the game or during configuration, which returns the client to the screen
 * below it. Releases before Minecraft 1.21.6 have no dialogs.
 */
@Value
@FieldNameConstants
public class ClientboundClearDialog implements ClientboundPacket {
}
