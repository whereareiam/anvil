package me.whereareiam.anvil.capability.overlays.model;

import lombok.Value;
import me.whereareiam.anvil.capability.overlays.type.BossBarChange;
import org.jetbrains.annotations.NotNull;

/**
 * One change of a boss bar shown to the player.
 */
@Value
public class BossBarEvent {
	/**
	 * Whether the bar appeared, changed or disappeared.
	 */
	@NotNull BossBarChange change;
	/**
	 * The bar after the change; for a removed bar, the bar as it was last shown.
	 */
	@NotNull BossBar bar;
}
