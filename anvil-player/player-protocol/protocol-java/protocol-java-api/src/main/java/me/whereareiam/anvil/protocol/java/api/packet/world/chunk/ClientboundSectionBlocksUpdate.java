package me.whereareiam.anvil.protocol.java.api.packet.world.chunk;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.BlockChange;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server changes several blocks of a chunk column the player has, which the client applies as one change. From
 * Minecraft 1.16.2 the game sends the changes of one 16-block section in this packet. Minecraft 1.16 could also send
 * part of a column in a chunk packet, replacing the sections it carried, which that release reports as this update of
 * every block of those sections: up to 4096 changes per section, from several sections of the column.
 */
@Value
public class ClientboundSectionBlocksUpdate implements ClientboundPacket {
	/**
	 * The changed blocks, in the order the server sent them.
	 */
	@NotNull List<BlockChange> changes;
}
