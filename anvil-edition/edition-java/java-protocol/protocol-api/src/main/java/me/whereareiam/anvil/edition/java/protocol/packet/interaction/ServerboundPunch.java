package me.whereareiam.anvil.edition.java.protocol.packet.interaction;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;

/**
 * The player punches, which the client reports after every attack from Minecraft 26.3 on. Earlier releases have no
 * such packet.
 */
@Value
public class ServerboundPunch implements ServerboundPacket {
}
