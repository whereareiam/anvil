package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.world.DimensionType;
import me.whereareiam.anvil.protocol.java.api.packet.ClientboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.protocol.java.api.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.protocol.java.api.type.CardinalLight;
import me.whereareiam.anvil.protocol.java.api.type.Skybox;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTranslation;
import net.kyori.adventure.key.Key;
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

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * The moments the MCProtocolLib releases from 1.20.5 on join a level, respawn and return to the configuration phase.
 * These releases name the dimension type by its ID in the {@code minecraft:dimension_type} registry the configuration
 * synchronized, so the translation reads that registry's entries as they arrive, and leaves the registry data itself
 * to the release's configuration translation; one translation serves one session. A vanilla type may arrive without
 * its description, which the client takes from its own data pack. A description names the type's effects before
 * Minecraft 1.21.11, and its skybox and shading from then on; one without either takes the overworld's. From
 * Minecraft 26.1 it also names the world clock its day follows. Each release's {@code ConnectionPackets} reads an
 * entry's description, whose type Minecraft 26.3 widened to any tag, and names the clock of a vanilla type sent without
 * one.
 */
public abstract class ConnectionTranslation implements PacketTranslation<Packet> {
	private static final String DIMENSION_TYPES = "minecraft:dimension_type";

	private volatile List<DimensionType> types = List.of();

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundLogin.class, ClientboundRespawn.class, ClientboundStartConfiguration.class);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
			case ClientboundRegistryDataPacket registry -> {
				if (registry.getRegistry().asString().equals(DIMENSION_TYPES))
					types = registry.getEntries().stream()
							.map(this::type)
							.toList();
				yield null;
			}
			case ClientboundLoginPacket login -> {
				PlayerSpawnInfo spawn = login.getCommonPlayerSpawnInfo();
				yield ClientboundLogin.builder()
						.entityId(login.getEntityId())
						.levels(Arrays.stream(login.getWorldNames()).map(Key::asString).toList())
						.hardcore(login.isHardcore())
						.dimension(spawn.getWorldName().asString())
						.dimensionType(type(spawn.getDimension()))
						.debug(spawn.isDebug())
						.flat(spawn.isFlat())
						.showDeathScreen(login.isEnableRespawnScreen())
						.build();
			}
			case ClientboundRespawnPacket respawn -> {
				PlayerSpawnInfo spawn = respawn.getCommonPlayerSpawnInfo();
				yield ClientboundRespawn.builder()
						.dimension(spawn.getWorldName().asString())
						.dimensionType(type(spawn.getDimension()))
						.debug(spawn.isDebug())
						.flat(spawn.isFlat())
						.build();
			}
			case ClientboundStartConfigurationPacket ignored -> new ClientboundStartConfiguration();
			default -> null;
		};
	}

	@Override
	public final @Nullable Packet encode(@NotNull ServerboundPacket packet) {
		return null;
	}

	/**
	 * Reads the description of a dimension type entry.
	 *
	 * @param entry the release's registry entry
	 * @return the description, or {@code null} when the server left it to a data pack the client knows
	 */
	protected abstract @Nullable NbtMap description(@NotNull RegistryEntry entry);

	/**
	 * Names the world clock of a vanilla type the server sent without its description.
	 *
	 * @param type the type's key
	 * @return the clock's key, or {@code null} before Minecraft 26.1, which has no world clocks
	 */
	protected @Nullable String knownClock(@NotNull String type) {
		return null;
	}

	/**
	 * Names the world clock of a vanilla type from Minecraft 26.1 on: the overworld's types follow the overworld's
	 * clock, the end's the end's, and the nether's none.
	 *
	 * @param type the type's key
	 * @return the clock's key, or {@code null} for a type without a clock or no vanilla type
	 */
	protected static @Nullable String vanillaClock(@NotNull String type) {
		return switch (type) {
			case "minecraft:overworld", "minecraft:overworld_caves" -> "minecraft:overworld";
			case "minecraft:the_end" -> "minecraft:the_end";
			default -> null;
		};
	}

	/**
	 * Describes a dimension type entry: by its skybox and shading from Minecraft 1.21.11 on, by its effects before, and
	 * by the vanilla type a client knows when the server sent no description; with its world clock from Minecraft 26.1.
	 */
	private @NotNull DimensionType type(@NotNull RegistryEntry entry) {
		String key = entry.getId().asString();
		NbtMap description = description(entry);
		if (description == null) return DimensionType.known(key).withDefaultClock(knownClock(key));
		if (description.containsKey("effects") && !description.containsKey("skybox"))
			return DimensionType.withEffects(key, description.getString("effects"));

		return new DimensionType(key, Skybox.named(description.getString("skybox", "overworld")),
				CardinalLight.named(description.getString("cardinal_light", "default")),
				description.containsKey("default_clock") ? description.getString("default_clock") : null);
	}

	private @NotNull DimensionType type(int id) {
		List<DimensionType> known = types;
		if (id < 0 || id >= known.size())
			throw new IllegalStateException("The server sent the dimension type " + id + ", which its configuration did not list");

		return known.get(id);
	}
}
