package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerRespawnPacket;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * The moments the MCProtocolLib 1.16.5 release joins a level and respawns, naming the level by its key. The release
 * has no configuration phase, so it never returns to one, and its levels have no height range of their own: each
 * spans the 256 blocks from height 0.
 */
public final class ConnectionPackets implements PacketTranslation<Packet> {
	private static final int MIN_Y = 0;
	private static final int HEIGHT = 256;

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundLogin.class, ClientboundRespawn.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ServerJoinGamePacket login)
			return new ClientboundLogin(login.getEntityId(), new Dimension(login.getWorldName(), MIN_Y, HEIGHT));
		if (packet instanceof ServerRespawnPacket respawn)
			return new ClientboundRespawn(new Dimension(respawn.getWorldName(), MIN_Y, HEIGHT));

		return null;
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}
}
