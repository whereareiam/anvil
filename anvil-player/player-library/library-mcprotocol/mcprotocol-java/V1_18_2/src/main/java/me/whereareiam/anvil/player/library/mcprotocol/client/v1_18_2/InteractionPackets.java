package me.whereareiam.anvil.player.library.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItemOn;
import org.jetbrains.annotations.NotNull;

/**
 * Interaction of the MCProtocolLib 1.18.2 release, whose item and block uses carry neither the block-change sequence
 * nor the view.
 */
final class InteractionPackets extends InteractionTranslation {
	@Override
	protected @NotNull Packet useItem(@NotNull ServerboundUseItem use) {
		return new ServerboundUseItemPacket(ValueTranslator.hand(use.hand()));
	}

	@Override
	protected @NotNull Packet useItemOn(@NotNull ServerboundUseItemOn use) {
		BlockPosition position = use.position();
		return new ServerboundUseItemOnPacket(new Position(position.x(), position.y(), position.z()),
				ValueTranslator.direction(use.face()), ValueTranslator.hand(use.hand()), use.cursorX(),
				use.cursorY(), use.cursorZ(), use.insideBlock());
	}
}
