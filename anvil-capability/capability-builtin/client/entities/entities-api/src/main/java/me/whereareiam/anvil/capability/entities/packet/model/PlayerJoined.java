package me.whereareiam.anvil.capability.entities.packet.model;

import lombok.Value;

/**
 * The server let the player into a world and gave it an entity ID; the client forgets every entity it knew.
 */
@Value
public class PlayerJoined implements EntityPacket {
	/**
	 * The player's own entity ID, which passengers of the player name as their vehicle.
	 */
	int entityId;
}
