package me.whereareiam.anvil.capability.entities.packet.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The entities riding a vehicle are now exactly these; earlier passengers missing from the list dismounted. The
 * vehicle may be the player itself.
 */
@Value
public class PassengersChanged implements EntityPacket {
	int vehicleId;
	@NotNull List<Integer> passengerIds;
}
