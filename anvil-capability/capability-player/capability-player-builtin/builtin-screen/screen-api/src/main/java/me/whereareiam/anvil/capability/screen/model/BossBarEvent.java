package me.whereareiam.anvil.capability.screen.model;

import lombok.Value;
import me.whereareiam.anvil.capability.screen.type.BossBarChange;
import org.jetbrains.annotations.NotNull;

/**
 * One change of a boss bar on the player's screen.
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
