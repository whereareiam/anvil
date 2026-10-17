package me.whereareiam.anvil.protocol.java.api.model.screen;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.protocol.java.api.type.BossEventColor;
import me.whereareiam.anvil.protocol.java.api.type.BossEventOverlay;
import org.jetbrains.annotations.NotNull;

/**
 * Adds a boss bar with all of its parts.
 */
@Value
@Builder
public class BossEventAdd implements BossEventOperation {
	/**
	 * The bar's text as plain text.
	 */
	@NotNull String name;
	/**
	 * How full the bar is, from {@code 0} to {@code 1}.
	 */
	float progress;
	/**
	 * The bar's color.
	 */
	@NotNull BossEventColor color;
	/**
	 * Whether the bar is one bar or divided into notches.
	 */
	@NotNull BossEventOverlay overlay;
	/**
	 * Whether the bar darkens the sky.
	 */
	boolean darkenScreen;
	/**
	 * Whether the bar plays the dragon fight music.
	 */
	boolean playMusic;
	/**
	 * Whether the bar creates fog.
	 */
	boolean createWorldFog;
}
