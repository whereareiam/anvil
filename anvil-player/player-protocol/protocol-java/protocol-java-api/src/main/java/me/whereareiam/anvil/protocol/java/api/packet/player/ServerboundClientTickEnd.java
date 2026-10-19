package me.whereareiam.anvil.protocol.java.api.packet.player;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;

/**
 * The client ends one of its ticks, after the packets that tick sent. Releases before Minecraft 1.21.2 have no such
 * packet.
 */
@Value
public class ServerboundClientTickEnd implements ServerboundPacket {
}
