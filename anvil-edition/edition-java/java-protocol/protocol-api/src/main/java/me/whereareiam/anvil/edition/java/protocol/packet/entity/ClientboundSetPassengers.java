package me.whereareiam.anvil.edition.java.protocol.packet.entity;

import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The entities riding a vehicle are now exactly these; earlier passengers missing from the list dismounted. The
 * vehicle may be the player itself.
 */
@Value
@FieldNameConstants
public class ClientboundSetPassengers implements ClientboundPacket {
	/**
	 * The vehicle's protocol ID.
	 */
	@NotNull EntityId vehicleId;
	/**
	 * The protocol IDs of the entities riding it.
	 */
	@NotNull List<EntityId> passengerIds;
}
