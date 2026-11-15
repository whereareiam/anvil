package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.java.api.model.configuration.RegistryEntry;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtCompound;
import me.whereareiam.anvil.protocol.java.api.model.nbt.NbtString;
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
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundRespawnPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Set;

/**
 * The moments the MCProtocolLib releases from 1.20.5 on join a level, respawn and return to the configuration phase.
 * These releases name the dimension type by its ID in the {@code minecraft:dimension_type} registry, which the
 * translation looks up in the connection's {@link SynchronizedRegistries}. A vanilla type arrives without its
 * description when the client named the vanilla core pack among those it knows, and the client takes it from its own
 * data pack, as {@link DimensionType#known(String)} describes it. A description gives the type's block heights, names
 * its effects before Minecraft 1.21.11 and its skybox and shading from then on; one without either takes the
 * overworld's. From Minecraft 26.1 it also names the world clock its day follows. Each release's
 * {@code ConnectionPackets} names the clock of a vanilla type sent without its description.
 *
 * <p>A type the configuration did not list, a description without block heights, and a type without its description
 * that no vanilla client knows break the protocol: a vanilla client cannot read the packet and disconnects, and the
 * translation throws, which makes MCProtocolLib disconnect the session as well.</p>
 */
public abstract class ConnectionTranslation implements PacketTranslation<Packet> {
	private static final String DIMENSION_TYPES = "minecraft:dimension_type";

	private final SynchronizedRegistries registries;

	/**
	 * Creates the connection translation of one connection.
	 *
	 * @param registries the connection's registries, in which the translation looks up dimension types
	 */
	protected ConnectionTranslation(@NotNull SynchronizedRegistries registries) {
		this.registries = registries;
	}

	@Override
	public final @NotNull Set<Class<? extends JavaPacket>> packets() {
		return Set.of(ClientboundLogin.class, ClientboundRespawn.class, ClientboundStartConfiguration.class);
	}

	@Override
	public final @Nullable ClientboundPacket decode(@NotNull Packet packet) {
		return switch (packet) {
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

	private @NotNull DimensionType type(int id) {
		RegistryEntry entry = registries.entry(DIMENSION_TYPES, id);
		if (entry == null)
			throw new IllegalStateException("The server sent the dimension type " + id
					+ ", which its configuration did not list");

		NbtCompound description = entry.getData();
		return description == null ? known(entry.getKey()) : described(entry.getKey(), description);
	}

	/**
	 * Describes a vanilla type the server left to the client's own data pack, with its world clock from Minecraft 26.1.
	 */
	private @NotNull DimensionType known(@NotNull String key) {
		DimensionType known = DimensionType.known(key);
		if (known == null)
			throw new IllegalStateException("The server sent the dimension type " + key + " without its description, "
					+ "which only a vanilla type may lack");

		return known.toBuilder().defaultClock(knownClock(key)).build();
	}

	/**
	 * Describes a type by its skybox and shading from Minecraft 1.21.11 on and by its effects before, with its block
	 * heights and, from Minecraft 26.1, its world clock.
	 */
	private static @NotNull DimensionType described(@NotNull String key, @NotNull NbtCompound description) {
		int minY = integer(key, description, "min_y");
		int height = integer(key, description, "height");
		String effects = string(description, "effects");
		if (effects != null && !description.contains("skybox"))
			return DimensionType.withEffects(key, effects, minY, height);

		String skybox = string(description, "skybox");
		String cardinalLight = string(description, "cardinal_light");
		return DimensionType.builder()
				.key(key)
				.skybox(Skybox.named(skybox == null ? "overworld" : skybox))
				.cardinalLight(CardinalLight.named(cardinalLight == null ? "default" : cardinalLight))
				.defaultClock(string(description, "default_clock"))
				.minY(minY)
				.height(height)
				.build();
	}

	private static int integer(@NotNull String key, @NotNull NbtCompound description, @NotNull String field) {
		return description.number(field)
				.map(number -> number.number().intValue())
				.orElseThrow(() -> new IllegalStateException("The server described the dimension type " + key
						+ " without its " + field));
	}

	private static @Nullable String string(@NotNull NbtCompound description, @NotNull String field) {
		return description.string(field).map(NbtString::getValue).orElse(null);
	}
}
