package me.whereareiam.anvil.protocol.mcprotocol.client.v1_19_4;

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
import me.whereareiam.anvil.protocol.mcprotocol.client.v1_18_2.ValueTranslator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The moments the MCProtocolLib 1.19.4 release joins a level and respawns. The release has no configuration phase,
 * so it never returns to one. It names the dimension type by its key and sends the dimension types of the whole server
 * with the login, which give each type its effects, block heights and whether it is {@code ultrawarm}, whose lava is
 * fast; one translation serves one session. A type the login did not list, or described without its block heights or
 * that flag, breaks the protocol: a vanilla client cannot read the
 * packet and disconnects, and the translation throws, which makes MCProtocolLib disconnect the session as well.
 */
public final class ConnectionPackets implements PacketTranslation<Packet> {
	private static final String DEFAULT_EFFECTS = "minecraft:overworld";

	private volatile Map<String, DimensionType> types = Map.of();
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
			types = types(login.getRegistry());
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

	private @NotNull DimensionType type(@NotNull String key) {
		DimensionType type = types.get(key);
		if (type == null)
			throw new IllegalStateException("The server sent the dimension type " + key + ", which its login did not list");

		return type;
	}

	/**
	 * Reads each dimension type of the login's registries, by key.
	 */
	private static @NotNull Map<String, DimensionType> types(@NotNull CompoundTag registries) {
		CompoundTag registry = registries.get("minecraft:dimension_type");
		ListTag entries = registry.get("value");
		Map<String, DimensionType> types = new HashMap<>();
		for (Tag entry : entries.getValue()) {
			CompoundTag type = (CompoundTag) entry;
			String key = ((StringTag) type.get("name")).getValue();
			types.put(key, described(key, type.get("element")));
		}

		return Map.copyOf(types);
	}

	private static @NotNull DimensionType described(@NotNull String key, @NotNull CompoundTag description) {
		StringTag described = description.get("effects");
		String effects = described == null ? DEFAULT_EFFECTS : described.getValue();
		return DimensionType.withEffects(key, effects, height(key, description, "min_y"),
						height(key, description, "height"))
				.toBuilder()
				.fastLava(ultrawarm(key, description))
				.build();
	}

	/**
	 * Reads whether a type is {@code ultrawarm}, which makes its lava fast. The vanilla client reads the flag as a
	 * number whose low byte is not zero and cannot read a type without it.
	 */
	private static boolean ultrawarm(@NotNull String key, @NotNull CompoundTag description) {
		Tag flag = description.get("ultrawarm");
		if (flag == null || !(flag.getValue() instanceof Number number))
			throw new IllegalStateException("The server described the dimension type " + key
					+ " without its ultrawarm");

		return number.byteValue() != 0;
	}

	private static int height(@NotNull String key, @NotNull CompoundTag description, @NotNull String field) {
		IntTag value = description.get(field);
		if (value == null)
			throw new IllegalStateException("The server described the dimension type " + key + " without its " + field);

		return value.getValue();
	}
}
