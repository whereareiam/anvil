package me.whereareiam.anvil.protocol.java.api.packet.interaction;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;

/**
 * The player punches, which the client reports after every attack from Minecraft 26.3 on. Earlier releases have no
 * such packet.
 */
@Value
public class ServerboundPunch implements ServerboundPacket {
}
