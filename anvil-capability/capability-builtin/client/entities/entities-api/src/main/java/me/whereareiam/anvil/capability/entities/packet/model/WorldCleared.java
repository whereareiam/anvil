package me.whereareiam.anvil.capability.entities.packet.model;

import lombok.Value;

/**
 * The client forgets every entity it knew: the player respawned or changed world, the server started configuring
 * the connection again, or the connection ended.
 */
@Value
public class WorldCleared implements EntityPacket {
}
