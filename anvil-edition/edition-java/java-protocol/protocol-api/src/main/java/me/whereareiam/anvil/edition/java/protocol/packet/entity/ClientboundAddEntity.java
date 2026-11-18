package me.whereareiam.anvil.edition.java.protocol.packet.entity;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityTypeKey;
import me.whereareiam.anvil.edition.java.protocol.model.world.Vec3d;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The server adds an entity to the player's view. Releases that add some entities through packets of their own, such
 * as mobs, players and paintings before Minecraft 1.19 or experience orbs before 1.21.2, report those as this packet
 * too: a painting at the corner of the block it hangs at, facing no direction.
 */
@Value
@FieldNameConstants
@Builder
public class ClientboundAddEntity implements ClientboundPacket {
	/**
	 * The entity's protocol ID, which later packets about it name.
	 */
	@NotNull EntityId entityId;
	/**
	 * The entity's unique ID, or {@code null} for an experience orb of a release before Minecraft 1.21.2, which added
	 * orbs without one.
	 */
	@Nullable UUID uniqueId;
	/**
	 * The entity's type.
	 */
	@NotNull EntityTypeKey type;
	/**
	 * Where the entity appears.
	 */
	@NotNull Vec3d position;
	/**
	 * The horizontal direction the entity faces, in degrees.
	 */
	float yaw;
	/**
	 * The vertical direction the entity faces, in degrees.
	 */
	float pitch;
}
