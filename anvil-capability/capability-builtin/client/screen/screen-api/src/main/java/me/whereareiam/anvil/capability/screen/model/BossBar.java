package me.whereareiam.anvil.capability.screen.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.capability.screen.type.BossBarColor;
import me.whereareiam.anvil.capability.screen.type.BossBarOverlay;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * A boss bar on the player's screen, as the server last described it.
 */
@Value
@Builder(toBuilder = true)
public class BossBar {
	/**
	 * The ID the server gave the bar; it stays the same while the bar is shown.
	 */
	@NotNull UUID id;
	/**
	 * The bar's text flattened to plain text.
	 */
	@NotNull String text;
	/**
	 * How full the bar is, from {@code 0} to {@code 1}.
	 */
	float progress;
	/**
	 * The bar's color.
	 */
	@NotNull BossBarColor color;
	/**
	 * Whether the bar is one bar or divided into notches.
	 */
	@NotNull BossBarOverlay overlay;
	/**
	 * Whether the bar darkens the sky.
	 */
	boolean darkensSky;
	/**
	 * Whether the bar plays the dragon fight music.
	 */
	boolean playsBossMusic;
	/**
	 * Whether the bar creates fog.
	 */
	boolean createsFog;
}
