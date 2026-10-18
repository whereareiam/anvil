package me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11;

import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.InteractionTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ValueTranslator;
import org.cloudburstmc.math.vector.Vector3i;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import org.jetbrains.annotations.NotNull;

/**
 * Interaction of the MCProtocolLib 1.21.11 release, whose item use carries the view and whose block use reports
 * whether it hit the world border.
 */
public final class InteractionPackets extends InteractionTranslation {
	@Override
	protected @NotNull Packet useItem(@NotNull ServerboundUseItem use) {
		return new ServerboundUseItemPacket(ValueTranslator.hand(use.getHand()), use.getSequence(), use.getYaw(),
				use.getPitch());
	}

	@Override
	protected @NotNull Packet useItemOn(@NotNull ServerboundUseItemOn use) {
		BlockPosition position = use.getPosition();
		return new ServerboundUseItemOnPacket(Vector3i.from(position.getX(), position.getY(), position.getZ()),
				ValueTranslator.direction(use.getFace()), ValueTranslator.hand(use.getHand()), use.getCursorX(),
				use.getCursorY(), use.getCursorZ(), use.isInsideBlock(), use.isWorldBorderHit(), use.getSequence());
	}
}
