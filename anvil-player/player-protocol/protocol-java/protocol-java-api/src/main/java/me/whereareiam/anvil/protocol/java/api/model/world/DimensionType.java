package me.whereareiam.anvil.protocol.java.api.model.world;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.type.CardinalLight;
import me.whereareiam.anvil.protocol.java.api.type.Skybox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The type of a dimension the server put the player in, with what decides how the client renders the level: its sky
 * and the shading of block faces. Releases before Minecraft 1.21.11 name both together as the type's effects, which
 * every release reports in this form; a type with a mod's own effects has neither. From Minecraft 26.1 a type also
 * names the world clock its day follows.
 */
@Value
public class DimensionType {
	private static final Map<String, DimensionType> VANILLA_EFFECTS = Map.of(
			"minecraft:overworld", new DimensionType("minecraft:overworld", Skybox.OVERWORLD, CardinalLight.DEFAULT),
			"minecraft:the_nether", new DimensionType("minecraft:the_nether", Skybox.NONE, CardinalLight.NETHER),
			"minecraft:the_end", new DimensionType("minecraft:the_end", Skybox.END, CardinalLight.DEFAULT)
	);

	/**
	 * The type's key in the {@code minecraft:dimension_type} registry, with its namespace, such as
	 * {@code minecraft:the_nether} or a data pack's {@code example:void}.
	 */
	@NotNull String key;
	/**
	 * The sky the client draws, or {@code null} when the type has a mod's own effects, or is no vanilla type and the
	 * server left its description to a data pack the client knows.
	 */
	@Nullable Skybox skybox;
	/**
	 * The shading of block faces, or {@code null} for the same types as the skybox.
	 */
	@Nullable CardinalLight cardinalLight;
	/**
	 * The key of the world clock whose time is the level's time of day, such as {@code minecraft:overworld}, or
	 * {@code null} before Minecraft 26.1, which has no world clocks, and for a type without a clock, such as the
	 * nether's.
	 */
	@Nullable String defaultClock;

	/**
	 * Describes a type without a world clock.
	 *
	 * @param key the type's key
	 * @param skybox the sky the client draws, or {@code null} when it is unknown
	 * @param cardinalLight the shading of block faces, or {@code null} when it is unknown
	 */
	public DimensionType(@NotNull String key, @Nullable Skybox skybox, @Nullable CardinalLight cardinalLight) {
		this(key, skybox, cardinalLight, null);
	}

	/**
	 * Describes a type.
	 *
	 * @param key the type's key
	 * @param skybox the sky the client draws, or {@code null} when it is unknown
	 * @param cardinalLight the shading of block faces, or {@code null} when it is unknown
	 * @param defaultClock the key of the world clock the type's day follows, or {@code null} when it has none
	 */
	public DimensionType(
			@NotNull String key,
			@Nullable Skybox skybox,
			@Nullable CardinalLight cardinalLight,
			@Nullable String defaultClock
	) {
		this.key = key;
		this.skybox = skybox;
		this.cardinalLight = cardinalLight;
		this.defaultClock = defaultClock;
	}

	/**
	 * Returns this type with the world clock its day follows.
	 *
	 * @param defaultClock the clock's key, or {@code null} when the type has none
	 * @return the type with that clock
	 */
	public @NotNull DimensionType withDefaultClock(@Nullable String defaultClock) {
		return new DimensionType(key, skybox, cardinalLight, defaultClock);
	}

	/**
	 * Describes a type by the effects that releases before Minecraft 1.21.11 name.
	 *
	 * @param key the type's key
	 * @param effects the type's effects, such as {@code minecraft:the_nether}
	 * @return the type, without a skybox or shading for a mod's own effects
	 */
	public static @NotNull DimensionType withEffects(@NotNull String key, @NotNull String effects) {
		DimensionType vanilla = VANILLA_EFFECTS.get(effects);
		return vanilla == null ? new DimensionType(key, null, null)
				: new DimensionType(key, vanilla.skybox, vanilla.cardinalLight);
	}

	/**
	 * Describes a vanilla type a client knows from its own data pack, which the server synchronized without its
	 * description.
	 *
	 * @param key the type's key
	 * @return the type, without a skybox or shading when it is no vanilla type
	 */
	public static @NotNull DimensionType known(@NotNull String key) {
		return withEffects(key, key.equals("minecraft:overworld_caves") ? "minecraft:overworld" : key);
	}
}
