package me.whereareiam.anvil.protocol.mcprotocol.client.v1_16_5;

import com.github.steveice10.mc.protocol.packet.ingame.server.ServerJoinGamePacket;
import com.github.steveice10.mc.protocol.packet.ingame.server.ServerRespawnPacket;
import com.github.steveice10.opennbt.tag.builtin.CompoundTag;
import com.github.steveice10.opennbt.tag.builtin.IntTag;
import com.github.steveice10.opennbt.tag.builtin.ListTag;
import com.github.steveice10.opennbt.tag.builtin.StringTag;
import com.github.steveice10.opennbt.tag.builtin.Tag;
import com.github.steveice10.packetlib.packet.Packet;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.type.player.GameMode;
import me.whereareiam.anvil.protocol.mcprotocol.client.translation.PacketTranslation;
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
 * description equals it, the first such entry when several are equal; one translation serves one session. A type of
 * Minecraft 1.16 names no block heights, because every level spans the 256 heights from 0; from Minecraft 1.17 a type
 * names both. Every type tells whether it is {@code ultrawarm}, whose lava is fast; a type without that flag breaks the
 * protocol, and the translation throws, which makes MCProtocolLib disconnect the session. The library names game mode
 * 255, which a server sends only for the absent previous mode, its unknown mode; the translation takes that mode for
 * survival, as the vanilla client takes a mode number it does not know.
 */
public final class ConnectionPackets implements PacketTranslation<Packet> {
	private static final String DEFAULT_EFFECTS = "minecraft:overworld";
	private static final String MIN_Y = "min_y";
	private static final String HEIGHT = "height";
	private static final String ULTRAWARM = "ultrawarm";
	private static final int FIXED_MIN_Y = 0;
	private static final int FIXED_HEIGHT = 256;

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
					.entityId(new EntityId(login.getEntityId()))
					.levels(List.of(login.getWorldNames()))
					.hardcore(login.isHardcore())
					.dimension(login.getWorldName())
					.dimensionType(type(login.getDimension()))
					.debug(login.isDebug())
					.flat(login.isFlat())
					.gameMode(gameMode(login.getGameMode()))
					.showDeathScreen(login.isEnableRespawnScreen())
					.build();
		}
		if (packet instanceof ServerRespawnPacket respawn)
			return ClientboundRespawn.builder()
					.dimension(respawn.getWorldName())
					.dimensionType(type(respawn.getDimension()))
					.debug(respawn.isDebug())
					.flat(respawn.isFlat())
					.gameMode(gameMode(respawn.getGamemode()))
					.build();

		return null;
	}

	@Override
	public @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	private static @NotNull GameMode gameMode(
			com.github.steveice10.mc.protocol.data.game.entity.player.@NotNull GameMode mode
	) {
		return switch (mode) {
			case SURVIVAL, UNKNOWN -> GameMode.SURVIVAL;
			case CREATIVE -> GameMode.CREATIVE;
			case ADVENTURE -> GameMode.ADVENTURE;
			case SPECTATOR -> GameMode.SPECTATOR;
		};
	}

	private @NotNull DimensionType type(@NotNull CompoundTag description) {
		StringTag described = description.get("effects");
		String effects = described == null ? DEFAULT_EFFECTS : described.getValue();
		for (Map.Entry<String, CompoundTag> type : types.entrySet())
			if (type.getValue().getValue().equals(description.getValue()))
				return DimensionType.withEffects(type.getKey(), effects, minY(description), height(description))
						.toBuilder()
						.fastLava(ultrawarm(description))
						.build();

		throw new IllegalStateException("The server sent a dimension type its login did not list");
	}

	/**
	 * Reads a type's lowest block height, which Minecraft 1.16 leaves out.
	 */
	private static int minY(@NotNull CompoundTag description) {
		IntTag minY = description.get(MIN_Y);
		return minY == null ? FIXED_MIN_Y : minY.getValue();
	}

	/**
	 * Reads the number of block heights a type's levels span, which Minecraft 1.16 leaves out.
	 */
	private static int height(@NotNull CompoundTag description) {
		IntTag height = description.get(HEIGHT);
		return height == null ? FIXED_HEIGHT : height.getValue();
	}

	/**
	 * Reads whether a type is {@code ultrawarm}, which makes its lava fast. The vanilla client reads the flag as a
	 * number whose low byte is not zero and cannot read a type without it.
	 */
	private static boolean ultrawarm(@NotNull CompoundTag description) {
		Tag flag = description.get(ULTRAWARM);
		if (flag == null || !(flag.getValue() instanceof Number number))
			throw new IllegalStateException("The server described a dimension type without its " + ULTRAWARM);

		return number.byteValue() != 0;
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
