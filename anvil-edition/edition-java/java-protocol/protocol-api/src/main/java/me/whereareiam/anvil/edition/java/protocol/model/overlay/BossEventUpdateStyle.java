package me.whereareiam.anvil.edition.java.protocol.model.overlay;

import lombok.Value;
import me.whereareiam.anvil.edition.java.protocol.type.overlay.BossEventColor;
import me.whereareiam.anvil.edition.java.protocol.type.overlay.BossEventOverlay;
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
