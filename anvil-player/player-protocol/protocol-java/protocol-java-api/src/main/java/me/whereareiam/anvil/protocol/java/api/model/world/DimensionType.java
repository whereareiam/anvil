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
 * every release reports in this form; a type with a mod's own effects has neither.
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
