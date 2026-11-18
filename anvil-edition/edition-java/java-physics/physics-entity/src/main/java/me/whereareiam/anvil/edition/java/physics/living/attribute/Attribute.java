package me.whereareiam.anvil.edition.java.physics.living.attribute;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An attribute of a living entity as vanilla's {@code RangedAttribute} registers it: its key in the names of Minecraft
 * 1.21.2, the release that made it an attribute, its default and the range {@code sanitizeValue} clamps it to. Before a
 * release has an attribute, its client uses the same number as a constant, which is the default here.
 * {@link LivingAttributes} holds those of every living entity; a kind of entity adds its own, as {@code Player} does.
 *
 * <pre>{@code
 * Attribute sneakingSpeed = Attribute.of("minecraft:sneaking_speed", "1.21", 0.3, 0.0, 1.0);
 * }</pre>
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class Attribute {
	private final @NotNull String key;
	@Getter(AccessLevel.NONE)
	private final @Nullable MinecraftVersion since;
	private final double defaultValue;
	private final double min;
	private final double max;

	/**
	 * Describes an attribute.
	 *
	 * @param key the attribute's key, such as {@code minecraft:gravity}
	 * @param since the first Minecraft version that has it, or {@code null} for one every supported version has
	 * @param defaultValue its default, which is also the constant of the versions before it
	 * @param min the lowest value it takes
	 * @param max the highest value it takes
	 * @return the attribute
	 */
	public static @NotNull Attribute of(
			@NotNull String key,
			@Nullable String since,
			double defaultValue,
			double min,
			double max
	) {
		return new Attribute(key, since == null ? null : MinecraftVersion.parse(since), defaultValue, min, max);
	}

	/**
	 * Tells whether a client of a version has this attribute.
	 *
	 * @param version the client's Minecraft version
	 * @return whether the server can send it
	 */
	public boolean existsIn(@NotNull MinecraftVersion version) {
		return since == null || version.isAtLeast(since);
	}

	/**
	 * Clamps a value to the attribute's range, as {@code RangedAttribute.sanitizeValue} does.
	 *
	 * @param value the computed value
	 * @return the value the game uses
	 */
	public double sanitize(double value) {
		return Double.isNaN(value) ? min : Math.clamp(value, min, max);
	}

	@Override
	public String toString() {
		return key;
	}
}
