package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * The moments the MCProtocolLib 1.19.4 release joins a level and respawns, naming the level by its key. Both packets
 * name the level's dimension type by its key; the login carries the dimension types in its registries, which the
 * translation remembers for the respawns of its connection. The release has no configuration phase, so it never
 * returns to one.
 */
public final class ConnectionPackets implements PacketTranslation<Packet> {
	private static final String DIMENSION_TYPES = "minecraft:dimension_type";

	private final ConnectionLevel level;

	/**
	 * The height range of each dimension type the last login named, by the type's key.
	 */
	private Map<String, Range> types = Map.of();

	/**
	 * Creates the translation of one connection.
	 *
	 * @param level the connection's level, which the translation records from each login and respawn
	 */
	public ConnectionPackets(@NotNull ConnectionLevel level) {
		this.level = level;
	}

	@Override
	public @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundLogin.class, ClientboundRespawn.class);
	}

	@Override
	public @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		if (packet instanceof ClientboundLoginPacket login) {
			types = types(login.getRegistry());
			return new ClientboundLogin(login.getEntityId(), dimension(login.getWorldName(), login.getDimension()));
		}
		if (packet instanceof ClientboundRespawnPacket respawn)
			return new ClientboundRespawn(dimension(respawn.getWorldName(), respawn.getDimension()));

		return null;
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	private Dimension dimension(String key, String type) {
		Range range = types.get(type);
		if (range == null)
			throw new IllegalStateException("The server named the dimension type " + type
					+ ", which its registries do not hold");

		Dimension dimension = new Dimension(key, range.minY(), range.height());
		level.enter(dimension);

		return dimension;
	}

	/**
	 * Reads the height range of each dimension type in the registries of a login.
	 */
	private static Map<String, Range> types(CompoundTag registries) {
		CompoundTag registry = registries.get(DIMENSION_TYPES);
		ListTag entries = registry.get("value");
		Map<String, Range> types = new HashMap<>();
		for (Tag tag : entries.getValue()) {
			CompoundTag entry = (CompoundTag) tag;
			StringTag name = entry.get("name");
			CompoundTag element = entry.get("element");
			IntTag minY = element.get("min_y");
			IntTag height = element.get("height");
			types.put(name.getValue(), new Range(minY.getValue(), height.getValue()));
		}

		return Map.copyOf(types);
	}

	private record Range(int minY, int height) {
	}
}
