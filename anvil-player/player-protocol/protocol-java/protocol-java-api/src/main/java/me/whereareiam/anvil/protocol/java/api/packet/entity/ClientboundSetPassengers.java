package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The entities riding a vehicle are now exactly these; earlier passengers missing from the list dismounted. The
 * vehicle may be the player itself.
 */
@Value
public class ClientboundSetPassengers implements ClientboundPacket {
	/**
	 * The vehicle's protocol ID.
	 */
	int vehicleId;
	/**
	 * The protocol IDs of the entities riding it.
	 */
	@NotNull List<Integer> passengerIds;
}
