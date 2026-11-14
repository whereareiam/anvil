package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The moments the MCProtocolLib releases from 1.16.5 until 1.17.1 join a level and respawn. These releases have no
 * configuration phase, so they never return to one. They send the dimension type itself, without its key, and the
 * dimension types of the whole server with the login, so the translation names a type by the login's entry whose
 * description equals it, the first such entry when several are equal; one translation serves one session.
 */
public final class ConnectionPackets implements PacketTranslation<Packet> {
	private static final String DEFAULT_EFFECTS = "minecraft:overworld";

	private volatile Map<String, CompoundTag> types = Map.of();

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundLogin.class, ClientboundRespawn.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ServerJoinGamePacket login) {
			types = types(login.getDimensionCodec());
			return ClientboundLogin.builder()
					.entityId(login.getEntityId())
					.levels(List.of(login.getWorldNames()))
					.hardcore(login.isHardcore())
					.dimension(login.getWorldName())
					.dimensionType(type(login.getDimension()))
					.debug(login.isDebug())
					.flat(login.isFlat())
					.showDeathScreen(login.isEnableRespawnScreen())
					.build();
		}
		if (packet instanceof ServerRespawnPacket respawn)
			return ClientboundRespawn.builder()
					.dimension(respawn.getWorldName())
					.dimensionType(type(respawn.getDimension()))
					.debug(respawn.isDebug())
					.flat(respawn.isFlat())
					.build();

		return null;
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	private @NotNull DimensionType type(@NotNull CompoundTag description) {
		StringTag described = description.get("effects");
		String effects = described == null ? DEFAULT_EFFECTS : described.getValue();
		for (Map.Entry<String, CompoundTag> type : types.entrySet())
			if (type.getValue().getValue().equals(description.getValue())) return DimensionType.withEffects(type.getKey(), effects);

		throw new IllegalStateException("The server sent a dimension type its login did not list");
	}

	/**
	 * Reads the dimension types of the login's registries, by key.
	 */
	private static @NotNull Map<String, CompoundTag> types(@NotNull CompoundTag registries) {
		CompoundTag registry = registries.get("minecraft:dimension_type");
		ListTag entries = registry.get("value");
		Map<String, CompoundTag> types = new LinkedHashMap<>();
		for (Tag entry : entries.getValue()) {
			CompoundTag type = (CompoundTag) entry;
			types.put(((StringTag) type.get("name")).getValue(), type.get("element"));
		}

		return Collections.unmodifiableMap(types);
	}
}
