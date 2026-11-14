package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The moments the MCProtocolLib 1.19.4 release joins a level and respawns. The release has no configuration phase,
 * so it never returns to one. It names the dimension type by its key and sends the dimension types of the whole server
 * with the login, which give each type its effects; one translation serves one session.
 */
public final class ConnectionPackets implements PacketTranslation<Packet> {
	private static final String DEFAULT_EFFECTS = "minecraft:overworld";

	private volatile Map<String, String> effects = Map.of();

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundLogin.class, ClientboundRespawn.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundLoginPacket login) {
			effects = effects(login.getRegistry());
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
		if (packet instanceof ClientboundRespawnPacket respawn)
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

	private @NotNull DimensionType type(@NotNull String key) {
		String type = effects.get(key);
		if (type == null)
			throw new IllegalStateException("The server sent the dimension type " + key + ", which its login did not list");

		return DimensionType.withEffects(key, type);
	}

	/**
	 * Reads the effects of each dimension type of the login's registries, by key.
	 */
	private static @NotNull Map<String, String> effects(@NotNull CompoundTag registries) {
		CompoundTag registry = registries.get("minecraft:dimension_type");
		ListTag entries = registry.get("value");
		Map<String, String> effects = new HashMap<>();
		for (Tag entry : entries.getValue()) {
			CompoundTag type = (CompoundTag) entry;
			CompoundTag element = type.get("element");
			StringTag described = element.get("effects");
			effects.put(((StringTag) type.get("name")).getValue(), described == null ? DEFAULT_EFFECTS : described.getValue());
		}

		return Map.copyOf(effects);
	}
}
