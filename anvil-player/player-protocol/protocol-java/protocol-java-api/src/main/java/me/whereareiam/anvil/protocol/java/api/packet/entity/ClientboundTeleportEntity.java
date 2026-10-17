package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.world.Vec3d;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.type.Relative;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * The server places an entity and turns it. Releases before Minecraft 1.21.2 teleport absolutely, so they name no
 * relative coordinate or angle.
 */
@Value
@Builder
public class ClientboundTeleportEntity implements ClientboundPacket {
	/**
	 * The entity's protocol ID.
	 */
	int entityId;
	/**
	 * The new position, each coordinate named in {@link #relatives} added to the current one.
	 */
	@NotNull Vec3d position;
	/**
	 * The new horizontal direction in degrees, added to the current one when {@link #relatives} names it.
	 */
	float yaw;
	/**
	 * The new vertical direction in degrees, added to the current one when {@link #relatives} names it.
	 */
	float pitch;
	/**
	 * The coordinates and angles that are relative to the current ones.
	 */
	@NotNull Set<Relative> relatives;
}
