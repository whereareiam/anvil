package me.whereareiam.anvil.protocol.java.api.packet.world.chunk;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.chunk.BlockChange;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server changes one block of a chunk column the player has.
 */
@Value
public class ClientboundBlockUpdate implements ClientboundPacket {
	/**
	 * The changed block.
	 */
	@NotNull BlockChange change;
}
