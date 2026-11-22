package me.whereareiam.anvil.player.library.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.AnimateAction;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6.ValueTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Animation;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Interaction of the MCProtocolLib 26.1 release, whose client swings its arm itself and whose server animates swings in the animate packet.
 */
final class InteractionPackets extends InteractionTranslation {
	@Override
	protected @NotNull AnimateAction action(@NotNull Animation animation) {
		return switch (animation) {
			case SWING_ARM -> AnimateAction.SWING_MAIN_HAND;
			case LEAVE_BED -> AnimateAction.WAKE_UP;
			case SWING_OFFHAND -> AnimateAction.SWING_OFF_HAND;
			case CRITICAL_HIT -> AnimateAction.CRITICAL_HIT;
			case ENCHANTMENT_CRITICAL_HIT -> AnimateAction.MAGIC_CRITICAL_HIT;
		};
	}

	@Override
	protected @NotNull Set<Class<? extends JavaPacket>> releasePackets() {
		return Set.of(ServerboundSwing.class);
	}

	@Override
	protected @Nullable Packet encodeRelease(@NotNull ServerboundPacket packet) {
		if (!(packet instanceof ServerboundSwing swing)) return null;

		return new ServerboundSwingPacket(ValueTranslator.hand(swing.getHand()));
	}
}
