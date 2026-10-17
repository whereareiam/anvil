package me.whereareiam.anvil.protocol.java.api.packet.dialog;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * Closes the dialog the player has open, in the game or during configuration, which returns the client to the screen
 * below it. Releases before Minecraft 1.21.6 have no dialogs.
 */
@Value
public class ClientboundClearDialog implements ClientboundPacket {
}
