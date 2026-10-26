package me.whereareiam.anvil.protocol.java.api.packet.connection;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server returns the player from the game to the configuration phase, such as a proxy does before it switches
 * the player to another server, and the client leaves the level, its chunks and its screens. Releases before
 * Minecraft 1.20.2 have no configuration phase.
 */
@Value
public class ClientboundStartConfiguration implements ClientboundPacket {
}
