package me.whereareiam.anvil.player.library.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.data.game.entity.metadata.Position;
import com.github.steveice10.mc.protocol.data.game.entity.player.Animation;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import com.github.steveice10.mc.protocol.packet.ingame.serverbound.player.ServerboundUseItemPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.model.world.BlockPosition;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItem;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundUseItemOn;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.AnimateAction;
import org.jetbrains.annotations.NotNull;

/**
 * Interaction of the MCProtocolLib 1.18.2 release, whose item and block uses carry neither the block-change sequence
 * nor the view, and whose server still animates the hurt entity.
 */
final class InteractionPackets extends InteractionTranslation {
	@Override
	protected @NotNull AnimateAction action(@NotNull Animation animation) {
		return switch (animation) {
			case SWING_ARM -> AnimateAction.SWING_MAIN_HAND;
			case DAMAGE -> AnimateAction.HURT;
			case LEAVE_BED -> AnimateAction.WAKE_UP;
			case SWING_OFFHAND -> AnimateAction.SWING_OFF_HAND;
			case CRITICAL_HIT -> AnimateAction.CRITICAL_HIT;
			case ENCHANTMENT_CRITICAL_HIT -> AnimateAction.MAGIC_CRITICAL_HIT;
		};
	}

	@Override
	protected @NotNull Packet useItem(@NotNull ServerboundUseItem use) {
		return new ServerboundUseItemPacket(ValueTranslator.hand(use.getHand()));
	}

	@Override
	protected @NotNull Packet useItemOn(@NotNull ServerboundUseItemOn use) {
		BlockPosition position = use.getPosition();
		return new ServerboundUseItemOnPacket(new Position(position.getX(), position.getY(), position.getZ()),
				ValueTranslator.direction(use.getFace()), ValueTranslator.hand(use.getHand()), use.getCursorX(),
				use.getCursorY(), use.getCursorZ(), use.isInsideBlock());
	}
}
