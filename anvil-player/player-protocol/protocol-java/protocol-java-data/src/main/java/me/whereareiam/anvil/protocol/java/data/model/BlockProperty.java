package me.whereareiam.anvil.protocol.java.data.model;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One state property of a block, such as a stair's {@code facing}, and the values it takes.
 *
 * <p>Values are in vanilla's order, which numbers the block's states: boolean properties are {@code true, false},
 * integer properties count up from their lowest value, and the last property of a block varies fastest between
 * consecutive state IDs.</p>
 */
@Value
public class BlockProperty {
	/**
	 * The property's name, such as {@code waterlogged}.
	 */
	@NotNull String name;
	/**
	 * The values the property takes, as vanilla writes them in a block state string.
	 */
	@NotNull List<String> values;
}
