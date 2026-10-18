package me.whereareiam.anvil.protocol.mcprotocol.client.v1_17_1;

import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
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
 * The moments the MCProtocolLib 1.17.1 release joins a level and respawns, naming the level by its key. Minecraft 1.17
 * gave levels a height range, which the dimension type that both packets carry whole names. The release has no
 * configuration phase, so it never returns to one.
 */
public final class ConnectionPackets implements PacketTranslation<Packet> {
	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundLogin.class, ClientboundRespawn.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ServerJoinGamePacket login)
			return new ClientboundLogin(login.getEntityId(), dimension(login.getWorldName(), login.getDimension()));
		if (packet instanceof ServerRespawnPacket respawn)
			return new ClientboundRespawn(dimension(respawn.getWorldName(), respawn.getDimension()));

		return null;
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Reads a level's height range from its dimension type.
	 */
	private static Dimension dimension(String key, CompoundTag type) {
		IntTag minY = type.get("min_y");
		IntTag height = type.get("height");
		return new Dimension(key, minY.getValue(), height.getValue());
	}
}
