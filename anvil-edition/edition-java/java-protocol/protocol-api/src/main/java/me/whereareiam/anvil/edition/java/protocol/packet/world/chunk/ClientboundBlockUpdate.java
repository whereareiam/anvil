package me.whereareiam.anvil.edition.java.protocol.packet.world.chunk;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.world.chunk.BlockChange;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server changes one block of a chunk column the player has.
 */
@Value
@FieldNameConstants
public class ClientboundBlockUpdate implements ClientboundPacket {
	/**
	 * The changed block.
	 */
	@NotNull BlockChange change;
}
