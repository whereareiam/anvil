package me.whereareiam.anvil.protocol.java.api.model.screen;

import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.type.BossEventColor;
import me.whereareiam.anvil.protocol.java.api.type.BossEventOverlay;
import org.jetbrains.annotations.NotNull;

/**
 * Changes a boss bar's color and overlay.
 */
@Value
public class BossEventUpdateStyle implements BossEventOperation {
	/**
	 * The bar's color.
	 */
	@NotNull BossEventColor color;
	/**
	 * Whether the bar is one bar or divided into notches.
	 */
	@NotNull BossEventOverlay overlay;
}
