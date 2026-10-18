package me.whereareiam.anvil.protocol.mcprotocol.client.v26_3;

import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.interaction.ServerboundPunch;
import me.whereareiam.anvil.protocol.mcprotocol.client.v26_1_2.InteractionTranslation;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundPunchPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Interaction of the MCProtocolLib 26.3 release, whose server plays the arm swing of every use itself, so the release
 * carries no swing; its client follows each attack with a punch.
 */
final class InteractionPackets extends InteractionTranslation {
	@Override
	protected @NotNull Set<Class<? extends JavaPacket>> releasePackets() {
		return Set.of(ServerboundPunch.class);
	}

	@Override
	protected @Nullable Packet encodeRelease(@NotNull ServerboundPacket packet) {
		return packet instanceof ServerboundPunch ? ServerboundPunchPacket.INSTANCE : null;
	}
}
