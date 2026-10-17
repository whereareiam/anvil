package me.whereareiam.anvil.capability.entities.packet.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The server spawned an entity for the player.
 */
@Value
@Builder
public class EntitySpawned implements EntityPacket {
	int entityId;
	/**
	 * Unique ID of the entity, or null for a packet that has none.
	 */
	@Nullable UUID uniqueId;
	/**
	 * Entity type with its namespace, such as {@code minecraft:armor_stand}.
	 */
	@NotNull String type;
	double x;
	double y;
	double z;
	float yaw;
	float pitch;
}
