package me.whereareiam.anvil.edition.java.data.block.generator.source;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.IntStream;

/**
 * The kinds of state property minecraft-data knows, each with how its values are written in vanilla's order.
 */
public enum PropertyType {
	/**
	 * A boolean property, whose values minecraft-data leaves out: vanilla's order is {@code true, false}.
	 */
	@JsonProperty("bool")
	BOOL {
		@Override
		public @NotNull List<String> values(@NotNull MinecraftDataProperty property, @Nullable Integer start) {
			return List.of("true", "false");
		}
	},
	/**
	 * An integer property, whose values count up from its lowest value: the listed values where minecraft-data lists
	 * them, which must start there, else counted from it.
	 */
	@JsonProperty("int")
	INT {
		@Override
		public @NotNull List<String> values(@NotNull MinecraftDataProperty property, @Nullable Integer start) {
			int lowest = start == null ? 0 : start;
			List<String> counted = IntStream.range(lowest, lowest + property.getValueCount())
					.mapToObj(Integer::toString)
					.toList();
			List<String> listed = property.getValues();
			if (listed != null && !listed.equals(counted))
				throw new IllegalStateException("integer property " + property.getName() + " lists " + listed + ", but "
						+ "block-facts.toml gives it the start " + lowest + "; add or fix its property-start");

			return counted;
		}
	},
	/**
	 * An enumerated property, whose values minecraft-data lists in vanilla's order. Some releases' data writes
	 * integer properties this way, such as leaves' {@code distance} in 1.16; one {@code block-facts.toml} gives a start
	 * is checked as an integer property.
	 */
	@JsonProperty("enum")
	ENUM {
		@Override
		public @NotNull List<String> values(@NotNull MinecraftDataProperty property, @Nullable Integer start) {
			List<String> listed = property.getValues();
			if (listed == null) throw new IllegalStateException("enum property " + property.getName() + " lists no values");
			if (start != null) return INT.values(property, start);

			return List.copyOf(listed);
		}
	};

	/**
	 * Returns a property's values in vanilla's order.
	 *
	 * @param property the property
	 * @param start the lowest value block-facts.toml gives an integer property, or {@code null} for {@code 0}
	 * @return the values
	 * @throws IllegalStateException when the file's values contradict vanilla's
	 */
	public abstract @NotNull List<String> values(@NotNull MinecraftDataProperty property, @Nullable Integer start);
}
