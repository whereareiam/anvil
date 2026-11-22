package me.whereareiam.anvil.testkit.physics.harness;

import lombok.Value;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A block state as vanilla writes it: a namespaced block ID and its properties,
 * {@code minecraft:oak_stairs[facing=north,half=bottom]}. Properties a state does not name keep the block's defaults.
 */
@Value
public class BlockStateText {
	private static final Pattern STATE = Pattern.compile("([a-z0-9_.-]+:[a-z0-9_./-]+)(?:\\[([a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*)])?");

	/**
	 * The block's namespaced ID, such as {@code minecraft:oak_stairs}.
	 */
	String block;

	/**
	 * The named properties in the order the text gives them.
	 */
	Map<String, String> properties;

	/**
	 * Parses {@code text}.
	 *
	 * @param text the state as vanilla writes it
	 * @return the parsed state
	 * @throws IllegalArgumentException when the text is not a block state
	 */
	public static BlockStateText parse(String text) {
		Matcher matcher = STATE.matcher(text);
		if (!matcher.matches())
			throw new IllegalArgumentException("'" + text + "' is not a block state such as minecraft:oak_stairs[facing=north]");

		Map<String, String> properties = new LinkedHashMap<>();
		if (matcher.group(2) != null)
			for (String property : matcher.group(2).split(",")) {
				String[] parts = property.split("=", 2);
				if (properties.put(parts[0], parts[1]) != null)
					throw new IllegalArgumentException("'" + text + "' names " + parts[0] + " twice");
			}
		return new BlockStateText(matcher.group(1), Collections.unmodifiableMap(properties));
	}

	@Override
	public String toString() {
		if (properties.isEmpty())
			return block;

		StringBuilder text = new StringBuilder(block).append('[');
		properties.forEach((name, value) -> text.append(name).append('=').append(value).append(','));
		text.setCharAt(text.length() - 1, ']');
		return text.toString();
	}
}
