package me.whereareiam.anvil.player.library.mcprotocol.client.v26_3;

import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.interaction.ServerboundPunch;
import me.whereareiam.anvil.edition.java.protocol.type.interaction.AnimateAction;
import me.whereareiam.anvil.player.library.mcprotocol.client.v26_1_2.InteractionTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Animation;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPunchPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Interaction of the MCProtocolLib 26.3 release, whose server plays the arm swing of every use itself, so the release
 * carries no swing and animates none in the animate packet; its client follows each attack with a punch.
 */
final class InteractionPackets extends InteractionTranslation {
	@Override
	protected @NotNull AnimateAction action(@NotNull Animation animation) {
		return switch (animation) {
			case WAKE_UP -> AnimateAction.WAKE_UP;
			case CRITICAL_HIT -> AnimateAction.CRITICAL_HIT;
			case MAGIC_CRITICAL_HIT -> AnimateAction.MAGIC_CRITICAL_HIT;
		};
	}

	@Override
	protected @NotNull Set<Class<? extends JavaPacket>> releasePackets() {
		return Set.of(ServerboundPunch.class);
	}

	@Override
	protected @Nullable Packet encodeRelease(@NotNull ServerboundPacket packet) {
		return packet instanceof ServerboundPunch ? ServerboundPunchPacket.INSTANCE : null;
	}
}
