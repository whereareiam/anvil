package me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5;

import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The moments the MCProtocolLib 1.16.5 release joins a level and respawns, which carries no packet of the area of its
 * own: it has no ping yet, and its servers send a rejected transaction of the player's inventory instead.
 */
final class ConnectionPackets extends ConnectionTranslation {
	@Override
	protected @NotNull Set<Class<? extends JavaPacket>> releasePackets() {
		return Set.of();
	}

	@Override
	protected @Nullable ClientboundPacket decodeRelease(@NotNull Packet packet) {
		return null;
	}

	@Override
	protected @Nullable Packet encodeRelease(@NotNull ServerboundPacket packet) {
		return null;
	}
}
