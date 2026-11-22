package me.whereareiam.anvil.player.library.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.client.ClientPongPacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerPingPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundPing;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ServerboundPong;
import me.whereareiam.anvil.player.library.mcprotocol.client.v1_16_5.ConnectionTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The moments the MCProtocolLib 1.17.1 release joins a level and respawns, and the ping Minecraft 1.17 introduced,
 * which the worker kit's client answers with its pong.
 */
final class ConnectionPackets extends ConnectionTranslation {
	@Override
	protected @NotNull Set<Class<? extends JavaPacket>> releasePackets() {
		return Set.of(ClientboundPing.class, ServerboundPong.class);
	}

	@Override
	protected @Nullable ClientboundPacket decodeRelease(@NotNull Packet packet) {
		return packet instanceof ServerPingPacket ping ? new ClientboundPing(ping.getId()) : null;
	}

	@Override
	protected @Nullable Packet encodeRelease(@NotNull ServerboundPacket packet) {
		return packet instanceof ServerboundPong pong ? new ClientPongPacket(pong.getId()) : null;
	}
}
