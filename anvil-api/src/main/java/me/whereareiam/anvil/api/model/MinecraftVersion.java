package me.whereareiam.anvil.api.model;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Release version of Minecraft: Java Edition, compared component by component.
 *
 * <p>Both numbering schemes order naturally: {@code 1.16.5 < 1.21.11 < 26.1 < 26.1.2}. Trailing zero
 * components are insignificant, so {@code 1.20} equals {@code 1.20.0}. Snapshots, pre-releases and
 * release candidates are rejected because no release data or version-specific code names them.</p>
 *
 * <pre>{@code
 * MinecraftVersion version = MinecraftVersion.parse("1.20.6");
 * boolean modern = version.compareTo(MinecraftVersion.parse("1.20.5")) >= 0;
 * }</pre>
 */
@Value
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MinecraftVersion implements Comparable<MinecraftVersion> {
	private static final Pattern RELEASE = Pattern.compile("\\d+(\\.\\d+){1,2}");

	/**
	 * Numeric components without trailing zeros.
	 */
	@NotNull List<Integer> components;

	/**
	 * Parses a dotted release version such as {@code 1.16.5}, {@code 1.20} or {@code 26.1.2}.
	 *
	 * @param text release version text
	 * @return the normalised version
	 * @throws IllegalArgumentException when the text is not a release version
	 */
	public static @NotNull MinecraftVersion parse(@NotNull String text) {
		if (!RELEASE.matcher(text).matches())
			throw new IllegalArgumentException("Not a Minecraft release version: '" + text + "'");

		List<Integer> parsed = new ArrayList<>();
		for (String component : text.split("\\."))
			parsed.add(Integer.parseInt(component));
		while (parsed.size() > 1 && parsed.getLast() == 0)
			parsed.removeLast();
		return new MinecraftVersion(List.copyOf(parsed));
	}

	/**
	 * Selects the value whose start version is the greatest one not newer than the target.
	 * Version-specific code and data apply from their start version until the next value starts.
	 *
	 * @param values candidate values in any order
	 * @param start start version of each value
	 * @param target version being served
	 * @param <T> value type
	 * @return the applicable value, or empty when every value starts after the target
	 */
	public static <T> @NotNull Optional<T> floor(
			@NotNull Collection<T> values,
			@NotNull Function<T, MinecraftVersion> start,
			@NotNull MinecraftVersion target
	) {
		T selected = null;
		MinecraftVersion selectedStart = null;
		for (T value : values) {
			MinecraftVersion candidate = start.apply(value);
			if (candidate.compareTo(target) > 0) continue;
			if (selectedStart != null && candidate.compareTo(selectedStart) <= 0) continue;

			selected = value;
			selectedStart = candidate;
		}

		return Optional.ofNullable(selected);
	}

	/**
	 * Tests whether this version is the same as or newer than another version.
	 *
	 * @param other version to compare with
	 * @return whether {@code this >= other}
	 */
	public boolean isAtLeast(@NotNull MinecraftVersion other) {
		return compareTo(other) >= 0;
	}

	@Override
	public int compareTo(@NotNull MinecraftVersion other) {
		int length = Math.max(components.size(), other.components.size());
		for (int index = 0; index < length; index++) {
			int difference = Integer.compare(component(index), other.component(index));
			if (difference != 0) return difference;
		}

		return 0;
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder();
		for (int index = 0; index < Math.max(2, components.size()); index++) {
			if (index > 0) text.append('.');
			text.append(component(index));
		}

		return text.toString();
	}

	private int component(int index) {
		return index < components.size() ? components.get(index) : 0;
	}
}
