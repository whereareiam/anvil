package me.whereareiam.anvil.protocol.java.api.packet.entity;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.model.interaction.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * The server adds a painting, which hangs at a block without a direction of its own; only releases before Minecraft
 * 1.19 add paintings separately.
 */
@Value
@Builder
public class ClientboundAddPainting implements ClientboundPacket {
	/**
	 * The entity's protocol ID, which later packets about it name.
	 */
	int entityId;
	/**
	 * The entity's unique ID.
	 */
	@NotNull UUID uniqueId;
	/**
	 * The block the painting hangs at.
	 */
	@NotNull BlockPosition position;
}
