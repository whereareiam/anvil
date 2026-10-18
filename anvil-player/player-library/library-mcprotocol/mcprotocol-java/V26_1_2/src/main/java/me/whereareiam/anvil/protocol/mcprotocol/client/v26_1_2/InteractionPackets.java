package me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2;

import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundSwing;
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6.ValueTranslator;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Interaction of the MCProtocolLib 26.1 release, whose client swings its arm itself.
 */
final class InteractionPackets extends InteractionTranslation {
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
