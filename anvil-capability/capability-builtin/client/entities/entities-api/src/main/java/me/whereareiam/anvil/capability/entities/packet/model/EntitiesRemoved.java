package me.whereareiam.anvil.capability.entities.packet.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The server removed entities from the player's view.
 */
@Value
public class EntitiesRemoved implements EntityPacket {
	@NotNull List<Integer> entityIds;
}
