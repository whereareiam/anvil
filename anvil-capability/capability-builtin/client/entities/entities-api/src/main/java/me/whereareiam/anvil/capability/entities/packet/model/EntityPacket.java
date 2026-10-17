package me.whereareiam.anvil.capability.entities.packet.model;

/**
 * What one packet says about the entities a player sees, decoded from a protocol-library release by its
 * {@link me.whereareiam.anvil.capability.entities.packet.EntitiesPackets} segment.
 */
public sealed interface EntityPacket permits PlayerJoined, PlayerMoved, WorldCleared, EntitySpawned, EntityMoved,
		EntityTurned, EntityDataChanged, PassengersChanged, EntitiesRemoved {
}
