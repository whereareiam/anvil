package me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2;

import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import com.github.steveice10.mc.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.protocol.java.api.model.entity.EntityId;
import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.model.world.Level;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The moments the MCProtocolLib 1.18.2 release joins a level and respawns. The release has no configuration phase,
 * so it never returns to one. It sends the dimension type itself, without its key, and the dimension types of the
 * whole server with the login, so the translation names a type by the login's entry whose
 * description equals it, the first such entry when several are equal; one translation serves one session. A type
 * names its block heights, and one without them breaks the protocol: a vanilla client cannot read the login and
 * disconnects, and the translation throws, which makes MCProtocolLib disconnect the session as well.
 */
public final class ConnectionPackets implements PacketTranslation<Packet> {
	private static final String DEFAULT_EFFECTS = "minecraft:overworld";
	private static final String MIN_Y = "min_y";
	private static final String HEIGHT = "height";

	private volatile Map<String, CompoundTag> types = Map.of();
	private final ConnectionLevel level;

	/**
	 * Creates the connection translation of one connection.
	 *
	 * @param level the connection's level, which the translation records for the connection's other translations
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
			types = types(login.getDimensionCodec());
			return entered(ClientboundLogin.builder()
					.entityId(new EntityId(login.getEntityId()))
					.levels(List.of(login.getWorldNames()))
					.hardcore(login.isHardcore())
					.gameMode(ValueTranslator.gameMode(login.getGameMode()))
					.dimension(login.getWorldName())
					.dimensionType(type(login.getDimension()))
					.debug(login.isDebug())
					.flat(login.isFlat())
					.showDeathScreen(login.isEnableRespawnScreen())
					.build());
		}
		if (packet instanceof ClientboundRespawnPacket respawn)
			return entered(ClientboundRespawn.builder()
					.dimension(respawn.getWorldName())
					.dimensionType(type(respawn.getDimension()))
					.gameMode(ValueTranslator.gameMode(respawn.getGamemode()))
					.debug(respawn.isDebug())
					.flat(respawn.isFlat())
					.build());

		return null;
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Records the level a login names for the connection's other translations.
	 */
	private ClientboundLogin entered(ClientboundLogin login) {
		level.enter(new Level(login.getDimension(), login.getDimensionType()));
		return login;
	}

	/**
	 * Records the level a respawn names for the connection's other translations.
	 */
	private ClientboundRespawn entered(ClientboundRespawn respawn) {
		level.enter(new Level(respawn.getDimension(), respawn.getDimensionType()));
		return respawn;
	}

	private @NotNull DimensionType type(@NotNull CompoundTag description) {
		StringTag described = description.get("effects");
		String effects = described == null ? DEFAULT_EFFECTS : described.getValue();
		for (Map.Entry<String, CompoundTag> type : types.entrySet())
			if (type.getValue().getValue().equals(description.getValue()))
				return DimensionType.withEffects(type.getKey(), effects, height(description, MIN_Y),
						height(description, HEIGHT));

		throw new IllegalStateException("The server sent a dimension type its login did not list");
	}

	/**
	 * Reads one of a type's block heights.
	 */
	private static int height(@NotNull CompoundTag description, @NotNull String field) {
		IntTag value = description.get(field);
		if (value == null)
			throw new IllegalStateException("The server described a dimension type without its " + field);

		return value.getValue();
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
