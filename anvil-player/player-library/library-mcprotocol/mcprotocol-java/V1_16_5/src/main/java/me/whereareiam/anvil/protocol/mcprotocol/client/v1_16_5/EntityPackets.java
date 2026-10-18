package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerEntityDestroyPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Entities of the MCProtocolLib 1.16.5 release, which removes entities through its destroy packet.
 */
final class EntityPackets extends EntityTranslation {
	EntityPackets() {
		super(EntityDataTable.LAYOUT);
	}

	@Override
	protected @Nullable ClientboundPacket decodeRemoval(@NotNull Packet packet) {
		if (!(packet instanceof ServerEntityDestroyPacket destroy)) return null;

		return removed(destroy.getEntityIds());
	}
}
