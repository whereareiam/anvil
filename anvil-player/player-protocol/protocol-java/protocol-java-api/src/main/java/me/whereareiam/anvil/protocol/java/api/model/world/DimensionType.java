package me.whereareiam.anvil.protocol.java.api.model.world;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.type.world.CardinalLight;
import me.whereareiam.anvil.protocol.java.api.type.world.Skybox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * The type of a dimension the server put the player in: the block heights its levels span, and what decides how the
 * client renders them, their sky and the shading of block faces. Releases before Minecraft 1.21.11 name sky and
 * shading together as the type's effects, which every release reports in this form; a type with a mod's own effects
 * has neither. From Minecraft 26.1 a type also names the world clock its day follows.
 *
 * <pre>{@code
 * DimensionType nether = DimensionType.builder()
 *         .key("minecraft:the_nether")
 *         .skybox(Skybox.NONE)
 *         .cardinalLight(CardinalLight.NETHER)
 *         .minY(0)
 *         .height(256)
 *         .build();
 * }</pre>
 */
@Value
@Builder(toBuilder = true)
public class DimensionType {
	private static final Map<String, Effects> VANILLA_EFFECTS = Map.of(
			"minecraft:overworld", new Effects(Skybox.OVERWORLD, CardinalLight.DEFAULT),
			"minecraft:the_nether", new Effects(Skybox.NONE, CardinalLight.NETHER),
			"minecraft:the_end", new Effects(Skybox.END, CardinalLight.DEFAULT)
	);

	/**
	 * The block heights of every vanilla type from Minecraft 1.20.5 on, the first release whose server may leave a
	 * type's description to the client's own data pack, until Minecraft 26.3.
	 */
	private static final Map<String, DimensionType> VANILLA = Map.of(
			"minecraft:overworld", withEffects("minecraft:overworld", "minecraft:overworld", -64, 384),
			"minecraft:overworld_caves", withEffects("minecraft:overworld_caves", "minecraft:overworld", -64, 384),
			"minecraft:the_nether", withEffects("minecraft:the_nether", "minecraft:the_nether", 0, 256),
			"minecraft:the_end", withEffects("minecraft:the_end", "minecraft:the_end", 0, 256)
	);

	/**
	 * The type's key in the {@code minecraft:dimension_type} registry, with its namespace, such as
	 * {@code minecraft:the_nether} or a data pack's {@code example:void}.
	 */
	@NotNull String key;
	/**
	 * The sky the client draws, or {@code null} when the type has a mod's own effects.
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
	 * The lowest block height of a level of the type, such as {@code -64} for the overworld from Minecraft 1.18 on.
	 */
	int minY;
	/**
	 * The number of block heights a level of the type spans from {@link #getMinY()} up, a multiple of 16: {@code 384}
	 * for the overworld from Minecraft 1.18 on, and {@code 256} for every type before Minecraft 1.17, whose levels had
	 * no other height.
	 */
	int height;

	/**
	 * Describes a type by the effects that releases before Minecraft 1.21.11 name.
	 *
	 * @param key the type's key
	 * @param effects the type's effects, such as {@code minecraft:the_nether}
	 * @param minY the lowest block height of the type's levels
	 * @param height the number of block heights the type's levels span
	 * @return the type, without a skybox or shading for a mod's own effects
	 */
	public static @NotNull DimensionType withEffects(
			@NotNull String key,
			@NotNull String effects,
			int minY,
			int height
	) {
		Effects vanilla = VANILLA_EFFECTS.get(effects);
		return DimensionType.builder()
				.key(key)
				.skybox(vanilla == null ? null : vanilla.skybox())
				.cardinalLight(vanilla == null ? null : vanilla.cardinalLight())
				.minY(minY)
				.height(height)
				.build();
	}

	/**
	 * Describes a vanilla type as a client from Minecraft 1.20.5 on knows it from its own data pack, which the server
	 * synchronized without its description because the client named that pack among those it knows.
	 *
	 * @param key the type's key
	 * @return the type without a world clock, or {@code null} when no vanilla type has that key
	 */
	public static @Nullable DimensionType known(@NotNull String key) {
		return VANILLA.get(key);
	}

	/**
	 * The sky and shading a vanilla type's effects stand for.
	 */
	private record Effects(Skybox skybox, CardinalLight cardinalLight) {
	}
}
