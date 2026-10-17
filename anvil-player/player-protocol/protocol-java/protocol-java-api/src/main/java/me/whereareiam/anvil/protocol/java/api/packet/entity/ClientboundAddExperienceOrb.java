package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * The server adds an experience orb, which has neither a unique ID nor a direction; only releases before Minecraft
 * 1.21.2 add orbs separately.
 */
@Value
@Builder
public class ClientboundAddExperienceOrb implements ClientboundPacket {
	/**
	 * The entity's protocol ID, which later packets about it name.
	 */
	int entityId;
	/**
	 * Where the entity appears.
	 */
	@NotNull EntityPosition position;
}
