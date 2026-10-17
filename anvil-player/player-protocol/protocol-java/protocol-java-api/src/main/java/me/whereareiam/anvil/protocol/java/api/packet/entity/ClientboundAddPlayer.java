package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The server adds another player to the player's view; only releases before Minecraft 1.20.2 add players
 * separately.
 */
@Value
@Builder
public class ClientboundAddPlayer implements ClientboundPacket {
	/**
	 * The entity's protocol ID, which later packets about it name.
	 */
	int entityId;
	/**
	 * The entity's unique ID.
	 */
	@NotNull UUID uniqueId;
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
