package me.whereareiam.anvil.player.library.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.edition.java.protocol.model.configuration.RegistryEntry;
import me.whereareiam.anvil.edition.java.protocol.model.entity.EntityId;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtCompound;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtNumber;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtString;
import me.whereareiam.anvil.edition.java.protocol.model.nbt.NbtTag;
import me.whereareiam.anvil.edition.java.protocol.model.world.DimensionType;
import me.whereareiam.anvil.edition.java.protocol.model.world.Level;
import me.whereareiam.anvil.edition.java.protocol.packet.ClientboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundLogin;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundRespawn;
import me.whereareiam.anvil.edition.java.protocol.packet.connection.ClientboundStartConfiguration;
import me.whereareiam.anvil.edition.java.protocol.type.player.GameMode;
import me.whereareiam.anvil.edition.java.protocol.type.world.CardinalLight;
import me.whereareiam.anvil.edition.java.protocol.type.world.Skybox;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.ConnectionLevel;
import me.whereareiam.anvil.player.library.mcprotocol.client.translation.PacketTranslation;
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
 * A login and a respawn name the game mode the player is in. These releases name the dimension type by its ID in the
 * {@code minecraft:dimension_type} registry, which the translation looks up in the connection's
 * {@link SynchronizedRegistries}. A vanilla type arrives without its description when the client named the vanilla
 * core pack among those it knows, and the client takes it from its own data pack, as
 * {@link DimensionType#known(String)} describes it. A description gives the type's block heights, names
 * its effects before Minecraft 1.21.11 and its skybox and shading from then on; one without either takes the
 * overworld's. From Minecraft 26.1 it also names the world clock its day follows. Each release's
 * {@code ConnectionPackets} names the clock of a vanilla type sent without its description, and reads whether a
 * described type's lava is fast where the release keeps it: the type's {@code ultrawarm} before Minecraft 1.21.11, its
 * {@code minecraft:gameplay/fast_lava} environment attribute from then on.
 *
 * <p>A type the configuration did not list, a description without block heights or its release's fast lava, and a
 * type without its description that no vanilla client knows break the protocol: a vanilla client cannot read the
 * packet and disconnects, and the translation throws, which makes MCProtocolLib disconnect the session as well.</p>
 */
public abstract class ConnectionTranslation implements PacketTranslation<Packet> {
	private static final String DIMENSION_TYPES = "minecraft:dimension_type";
	private static final String FAST_LAVA = "gameplay/fast_lava";
	private static final String DEFAULT_NAMESPACE = "minecraft:";

	private final SynchronizedRegistries registries;
	private final ConnectionLevel level;

	/**
	 * Creates the connection translation of one connection.
	 *
	 * @param registries the connection's registries, in which the translation looks up dimension types
	 * @param level the connection's level, which the translation records for the connection's other translations
	 */
	protected ConnectionTranslation(@NotNull SynchronizedRegistries registries, @NotNull ConnectionLevel level) {
		this.registries = registries;
		this.level = level;
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
				yield entered(ClientboundLogin.builder()
						.entityId(new EntityId(login.getEntityId()))
						.levels(Arrays.stream(login.getWorldNames()).map(Key::asString).toList())
						.hardcore(login.isHardcore())
						.dimension(spawn.getWorldName().asString())
						.dimensionType(type(spawn.getDimension()))
						.gameMode(gameMode(spawn.getGameMode()))
						.debug(spawn.isDebug())
						.flat(spawn.isFlat())
						.showDeathScreen(login.isEnableRespawnScreen())
						.build());
			}
			case ClientboundRespawnPacket respawn -> {
				PlayerSpawnInfo spawn = respawn.getCommonPlayerSpawnInfo();
				yield entered(ClientboundRespawn.builder()
						.dimension(spawn.getWorldName().asString())
						.dimensionType(type(spawn.getDimension()))
						.gameMode(gameMode(spawn.getGameMode()))
						.debug(spawn.isDebug())
						.flat(spawn.isFlat())
						.build());
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
	 * Records the level a login names for the connection's other translations.
	 */
	private ClientboundLogin entered(ClientboundLogin login) {
		level.enter(new Level(login.dimension(), login.dimensionType()));
		return login;
	}

	/**
	 * Records the level a respawn names for the connection's other translations.
	 */
	private ClientboundRespawn entered(ClientboundRespawn respawn) {
		level.enter(new Level(respawn.dimension(), respawn.dimensionType()));
		return respawn;
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
	 * Reads whether lava is fast in the levels of a type the server described, where the release keeps it.
	 *
	 * @param key the type's key
	 * @param description the type's description
	 * @return whether the type's lava is fast
	 * @throws IllegalStateException when the description lacks or breaks what the release reads, which a vanilla
	 * client cannot read either
	 */
	protected abstract boolean fastLava(@NotNull String key, @NotNull NbtCompound description);

	/**
	 * Reads a type's {@code ultrawarm} flag, which makes its lava fast before Minecraft 1.21.11 and which every type of
	 * those releases names.
	 *
	 * @param key the type's key
	 * @param description the type's description
	 * @return whether the type is ultrawarm
	 * @throws IllegalStateException when the description has no such number
	 */
	protected static boolean ultrawarm(@NotNull String key, @NotNull NbtCompound description) {
		return description.number("ultrawarm")
				.map(NbtNumber::asBoolean)
				.orElseThrow(() -> new IllegalStateException("The server described the dimension type " + key
						+ " without its ultrawarm"));
	}

	/**
	 * Reads a type's {@code minecraft:gameplay/fast_lava} environment attribute from Minecraft 1.21.11 on, as the
	 * client's dimension layer applies it to the attribute's default, {@code false}: a value replaces the default, an
	 * entry with a {@code modifier} and its {@code argument} combines them, and a type without the attribute keeps the
	 * default. The client names an attribute without a namespace in {@code minecraft}'s.
	 *
	 * @param key the type's key
	 * @param description the type's description
	 * @return whether the type's lava is fast
	 * @throws IllegalStateException when the attribute is neither a boolean nor a boolean modifier entry
	 */
	protected static boolean fastLavaAttribute(@NotNull String key, @NotNull NbtCompound description) {
		NbtCompound attributes = description.compound("attributes").orElse(NbtCompound.EMPTY);
		NbtTag entry = attribute(attributes);
		if (entry == null) return false;
		if (entry instanceof NbtNumber value) return value.asBoolean();
		if (!(entry instanceof NbtCompound modified))
			throw new IllegalStateException("The server described the dimension type " + key + " with a fast_lava "
					+ "attribute that is no boolean");

		String modifier = modified.string("modifier").map(NbtString::value).orElse(null);
		NbtNumber argument = modified.number("argument").orElse(null);
		if (modifier == null || argument == null)
			throw new IllegalStateException("The server described the dimension type " + key + " with a fast_lava "
					+ "modifier without its operation or argument");

		return modify(key, modifier, argument.asBoolean());
	}

	/**
	 * Finds the fast lava attribute among a type's attributes, under its key with or without the default namespace.
	 */
	private static @Nullable NbtTag attribute(@NotNull NbtCompound attributes) {
		NbtTag named = attributes.get(DEFAULT_NAMESPACE + FAST_LAVA);
		return named != null ? named : attributes.get(FAST_LAVA);
	}

	/**
	 * Applies a boolean modifier of the game to the attribute's default, {@code false} ({@code BooleanModifier}).
	 */
	private static boolean modify(@NotNull String key, @NotNull String modifier, boolean argument) {
		return switch (modifier) {
			case "override", "or", "xor" -> argument;
			case "nor", "xnor" -> !argument;
			case "and" -> false;
			case "nand" -> true;
			default -> throw new IllegalStateException("The server described the dimension type " + key
					+ " with the fast_lava modifier " + modifier + ", which no boolean attribute has");
		};
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
	 * Translates the game mode the server puts the player in, which every release from 1.20.5 on names alike.
	 */
	private static @NotNull GameMode gameMode(
			org.geysermc.mcprotocollib.protocol.data.game.entity.player.@NotNull GameMode gameMode
	) {
		return switch (gameMode) {
			case SURVIVAL -> GameMode.SURVIVAL;
			case CREATIVE -> GameMode.CREATIVE;
			case ADVENTURE -> GameMode.ADVENTURE;
			case SPECTATOR -> GameMode.SPECTATOR;
		};
	}

	private @NotNull DimensionType type(int id) {
		RegistryEntry entry = registries.entry(DIMENSION_TYPES, id);
		if (entry == null)
			throw new IllegalStateException("The server sent the dimension type " + id
					+ ", which its configuration did not list");

		NbtCompound description = entry.data();
		return description == null ? known(entry.key()) : described(entry.key(), description);
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
	 * heights, whether its lava is fast and, from Minecraft 26.1, its world clock.
	 */
	private @NotNull DimensionType described(@NotNull String key, @NotNull NbtCompound description) {
		int minY = integer(key, description, "min_y");
		int height = integer(key, description, "height");
		boolean fastLava = fastLava(key, description);
		String effects = string(description, "effects");
		if (effects != null && !description.contains("skybox"))
			return DimensionType.withEffects(key, effects, minY, height).toBuilder().fastLava(fastLava).build();

		String skybox = string(description, "skybox");
		String cardinalLight = string(description, "cardinal_light");
		return DimensionType.builder()
				.key(key)
				.skybox(Skybox.named(skybox == null ? "overworld" : skybox))
				.cardinalLight(CardinalLight.named(cardinalLight == null ? "default" : cardinalLight))
				.defaultClock(string(description, "default_clock"))
				.minY(minY)
				.height(height)
				.fastLava(fastLava)
				.build();
	}

	private static int integer(@NotNull String key, @NotNull NbtCompound description, @NotNull String field) {
		return description.number(field)
				.map(number -> number.number().intValue())
				.orElseThrow(() -> new IllegalStateException("The server described the dimension type " + key
						+ " without its " + field));
	}

	private static @Nullable String string(@NotNull NbtCompound description, @NotNull String field) {
		return description.string(field).map(NbtString::value).orElse(null);
	}
}
