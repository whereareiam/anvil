package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.world.Dimension;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.mcprotocol.client.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import org.cloudburstmc.nbt.NbtMap;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The moments the MCProtocolLib releases from 1.20.5 on join a level, respawn and return to the configuration phase,
 * naming the level by its key. Both the login and the respawn name the level's dimension type by its index in the
 * dimension type registry, which the server sends during configuration; the translation remembers that registry's
 * height ranges for its connection and reads the registry data without translating it, so a table that also carries
 * the registry data lists this translation before the one that does. It records each level it decodes as the
 * connection's level. Each release's {@code ConnectionPackets} reads a registry entry's data, whose type Minecraft 26.3
 * widened to any tag.
 */
public abstract class ConnectionTranslation implements PacketTranslation<Packet> {
	private static final String DIMENSION_TYPES = "minecraft:dimension_type";

	private final ConnectionLevel level;
	/**
	 * The height range of each dimension type of the last configuration, by registry index.
	 */
	private List<Range> types = List.of();

	/**
	 * Creates the translation of one connection.
	 *
	 * @param level the connection's level, which the translation records from each login and respawn
	 */
	protected ConnectionTranslation(@NotNull ConnectionLevel level) {
		this.level = level;
	}

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundLogin.class, ClientboundRespawn.class, ClientboundStartConfiguration.class);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundRegistryDataPacket registry -> remember(registry);
			case ClientboundLoginPacket login -> new ClientboundLogin(login.getEntityId(),
					dimension(login.getCommonPlayerSpawnInfo()));
			case ClientboundRespawnPacket respawn -> new ClientboundRespawn(dimension(respawn.getCommonPlayerSpawnInfo()));
			case ClientboundStartConfigurationPacket ignored -> new ClientboundStartConfiguration();
			default -> null;
		};
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Reads the data of a registry entry.
	 *
	 * @param entry the release's registry entry
	 * @return the entry's compound, or {@code null} when the server left the data out
	 */
	protected abstract @Nullable NbtMap data(@NotNull RegistryEntry entry);

	/**
	 * Remembers the height ranges of the dimension type registry, which is not translated here.
	 */
	private @Nullable ClientboundPacket remember(ClientboundRegistryDataPacket registry) {
		if (!registry.getRegistry().asString().equals(DIMENSION_TYPES)) return null;

		List<Range> ranges = new ArrayList<>(registry.getEntries().size());
		for (RegistryEntry entry : registry.getEntries()) {
			NbtMap data = data(entry);
			ranges.add(data == null ? null : new Range(data.getInt("min_y"), data.getInt("height")));
		}
		types = ranges;

		return null;
	}

	private Dimension dimension(PlayerSpawnInfo spawn) {
		int type = spawn.getDimension();
		Range range = type >= 0 && type < types.size() ? types.get(type) : null;
		if (range == null)
			throw new IllegalStateException("The server named dimension type " + type
					+ ", whose height its registry data does not give");

		Dimension dimension = new Dimension(spawn.getWorldName().asString(), range.minY(), range.height());
		level.enter(dimension);

		return dimension;
	}

	private record Range(int minY, int height) {
	}
}
