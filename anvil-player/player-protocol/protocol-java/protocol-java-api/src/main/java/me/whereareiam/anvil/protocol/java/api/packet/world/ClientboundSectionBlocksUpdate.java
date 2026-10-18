package me.whereareiam.anvil.protocol.java.api.packet.world;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockChange;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server changes several blocks of one section of a chunk column the player has. Minecraft 1.16 could also send
 * part of a column in a chunk packet, which the client applies block by block; that release reports such a packet as
 * this update of every block it carries.
 */
@Value
public class ClientboundSectionBlocksUpdate implements ClientboundPacket {
	/**
	 * The changed blocks, in the order the server sent them.
	 */
	@NotNull List<BlockChange> changes;
}
