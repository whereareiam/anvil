package me.whereareiam.anvil.edition.java.protocol.packet.entity.state;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server gives an entity an effect, such as speed or levitation, or changes one it has. The protocol models what
 * the effect does of the packet's fields, not how the client shows it: some releases' libraries drop its icon flag.
 */
@Value
@FieldNameConstants
@Builder
public class ClientboundUpdateMobEffect implements ClientboundPacket {
	/**
	 * The entity's protocol ID.
	 */
	@NotNull EntityId entityId;
	/**
	 * The effect's key with its namespace, such as {@code minecraft:speed}; releases that number their effects report
	 * the key of the number.
	 */
	@NotNull String effect;
	/**
	 * The effect's amplifier: 0 for level I, 1 for level II and so on.
	 */
	int amplifier;
	/**
	 * How many ticks the effect lasts, or {@code -1} when it lasts until removed.
	 */
	int duration;
}
