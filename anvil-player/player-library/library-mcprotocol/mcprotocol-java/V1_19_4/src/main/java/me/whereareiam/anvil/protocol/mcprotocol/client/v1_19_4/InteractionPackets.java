package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.world.BlockPosition;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.InteractionTranslation;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.ValueTranslator;
import org.cloudburstmc.math.vector.Vector3i;
import org.jetbrains.annotations.NotNull;

/**
 * Interaction of the MCProtocolLib 1.19.4 release, whose item and block uses carry the block-change sequence but not
 * yet the view.
 */
public final class InteractionPackets extends InteractionTranslation {
	@Override
	protected @NotNull Packet useItem(@NotNull ServerboundUseItem use) {
		return new ServerboundUseItemPacket(ValueTranslator.hand(use.getHand()), use.getSequence());
	}

	@Override
	protected @NotNull Packet useItemOn(@NotNull ServerboundUseItemOn use) {
		BlockPosition position = use.getPosition();
		return new ServerboundUseItemOnPacket(Vector3i.from(position.getX(), position.getY(), position.getZ()),
				ValueTranslator.direction(use.getFace()), ValueTranslator.hand(use.getHand()), use.getCursorX(),
				use.getCursorY(), use.getCursorZ(), use.isInsideBlock(), use.getSequence());
	}
}
