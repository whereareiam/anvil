package me.whereareiam.anvil.protocol.api.model.text.hover;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Shows an item's tooltip.
 */
@Value
public class ShowItemHover implements TextHover {
	/**
	 * The item's key, such as {@code minecraft:diamond_sword}.
	 */
	@NotNull String item;
	/**
	 * The number of items.
	 */
	int count;
}
