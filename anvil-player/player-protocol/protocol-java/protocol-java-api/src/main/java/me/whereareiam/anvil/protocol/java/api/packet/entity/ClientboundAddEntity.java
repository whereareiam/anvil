package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityPosition;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The server adds an entity to the player's view. Releases before Minecraft 1.19 add mobs, players and paintings
 * through packets of their own, releases before 1.20.2 players, and releases before 1.21.2 experience orbs.
 */
@Value
@Builder
public class ClientboundAddEntity implements ClientboundPacket {
	/**
	 * The entity's protocol ID, which later packets about it name.
	 */
	int entityId;
	/**
	 * The entity's unique ID.
	 */
	@NotNull UUID uniqueId;
	/**
	 * The entity's type.
	 */
	@NotNull EntityTypeKey type;
	/**
	 * Where the entity appears.
	 */
	@NotNull EntityPosition position;
	/**
	 * The horizontal direction the entity faces, in degrees.
	 */
	float yaw;
	/**
	 * The vertical direction the entity faces, in degrees.
	 */
	float pitch;
}
