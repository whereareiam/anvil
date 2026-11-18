package me.whereareiam.anvil.edition.java.protocol.packet.connection;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;

/**
 * The server returns the player from the game to the configuration phase, such as a proxy does before it switches
 * the player to another server, and the client leaves the level and its screens. Releases before Minecraft 1.20.2
 * have no configuration phase.
 */
@Value
@FieldNameConstants
public class ClientboundStartConfiguration implements ClientboundPacket {
}
