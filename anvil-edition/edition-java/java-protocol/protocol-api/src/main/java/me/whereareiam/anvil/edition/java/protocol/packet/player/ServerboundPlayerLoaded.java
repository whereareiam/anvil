package me.whereareiam.anvil.edition.java.protocol.packet.player;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;

/**
 * The client tells the server that it has loaded the level its player joined or respawned in, as the vanilla client
 * does once the level around its player is ready ({@code ClientPacketListener.notifyPlayerLoaded}); until then, or a
 * timeout, the server keeps the player invulnerable and ignores some of its actions. Releases before Minecraft 1.21.4
 * have no such packet.
 */
@Value
public class ServerboundPlayerLoaded implements ServerboundPacket {
}
