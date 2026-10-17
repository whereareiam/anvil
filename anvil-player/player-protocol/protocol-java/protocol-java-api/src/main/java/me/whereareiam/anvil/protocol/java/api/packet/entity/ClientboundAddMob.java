package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityPosition;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityTypeKey;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The server adds a living entity to the player's view; only releases before Minecraft 1.19 add mobs separately.
 */
@Value
@Builder
public class ClientboundAddMob implements ClientboundPacket {
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
