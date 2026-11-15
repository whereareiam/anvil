package me.whereareiam.anvil.protocol.java.block.generator.source;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One state property of a minecraft-data block, such as a stair's {@code facing}.
 */
@Value
@Builder
@Jacksonized
public class MinecraftDataProperty {
	/**
	 * The property's name.
	 */
	@NotNull String name;
	/**
	 * What kind of values the property takes.
	 */
	@NotNull PropertyType type;
	/**
	 * How many values the property takes.
	 */
	@JsonProperty("num_values")
	int valueCount;
	/**
	 * The values as the file lists them, or {@code null} where it leaves them out, as it does for booleans and, in
	 * some releases, for integers.
	 */
	@Nullable List<String> values;
}
