package me.whereareiam.anvil.protocol.java.api.packet.connection;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;

/**
 * The server respawns the player or moves it to another dimension, and the client replaces its screens with the new
 * level. The protocol models none of the packet's fields.
 */
@Value
public class ClientboundRespawn implements ClientboundPacket {
}
