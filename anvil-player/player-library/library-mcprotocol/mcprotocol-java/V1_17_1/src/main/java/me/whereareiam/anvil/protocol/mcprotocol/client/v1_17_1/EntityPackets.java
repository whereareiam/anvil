package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.server.entity.ServerRemoveEntitiesPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5.EntityTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Entities of the MCProtocolLib 1.17.1 release, which removes entities through the packet Minecraft 1.17 named after
 * the removal.
 */
final class EntityPackets extends EntityTranslation {
	EntityPackets() {
		super(EntityDataTable.LAYOUT);
	}

	@Override
	protected @Nullable ClientboundPacket decodeRemoval(@NotNull Packet packet) {
		if (!(packet instanceof ServerRemoveEntitiesPacket removed)) return null;

		return removed(removed.getEntityIds());
	}
}
