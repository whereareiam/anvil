package me.whereareiam.anvil.protocol.java.api.packet.connection;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server lets the player join the game, after login or configuration, and the client replaces its screens with
 * the level. The protocol models none of the packet's fields.
 */
@Value
public class ClientboundLogin implements ClientboundPacket {
}
