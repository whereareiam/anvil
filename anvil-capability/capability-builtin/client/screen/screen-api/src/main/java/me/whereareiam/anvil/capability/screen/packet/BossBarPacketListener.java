package me.whereareiam.anvil.capability.screen.packet;

import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.type.BossBarColor;
import me.whereareiam.anvil.capability.screen.type.BossBarOverlay;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Receives the boss bar packets of one release's session, translated by {@link ScreenPackets} into library-neutral
 * values, and the end of its connection. An update reports only the part of the bar the packet changes; the screen
 * binding keeps each whole bar.
 *
 * <pre>{@code
 * Runnable detach = packets.listenBossBars(session, new BossBarPacketListener() {
 *     public void added(BossBar bar) { bars.put(bar.getId(), bar); }
 *     public void progress(UUID id, float progress) { bars.computeIfPresent(id, ...); }
 *     // ...
 * });
 * }</pre>
 */
public interface BossBarPacketListener {
	/**
	 * Reports a boss bar the server added.
	 *
	 * @param bar the added bar
	 */
	void added(@NotNull BossBar bar);

	/**
	 * Reports that the server removed a boss bar.
	 *
	 * @param id the bar's ID
	 */
	void removed(@NotNull UUID id);

	/**
	 * Reports a boss bar's new progress.
	 *
	 * @param id the bar's ID
	 * @param progress how full the bar is, from {@code 0} to {@code 1}
	 */
	void progress(@NotNull UUID id, float progress);

	/**
	 * Reports a boss bar's new text.
	 *
	 * @param id the bar's ID
	 * @param text the bar's text as plain text
	 */
	void text(@NotNull UUID id, @NotNull String text);

	/**
	 * Reports a boss bar's new color and overlay.
	 *
	 * @param id the bar's ID
	 * @param color the bar's color
	 * @param overlay whether the bar is one bar or divided into notches
	 */
	void style(@NotNull UUID id, @NotNull BossBarColor color, @NotNull BossBarOverlay overlay);

	/**
	 * Reports a boss bar's new flags.
	 *
	 * @param id the bar's ID
	 * @param darkensSky whether the bar darkens the sky
	 * @param playsBossMusic whether the bar plays the dragon fight music
	 * @param createsFog whether the bar creates fog
	 */
	void flags(@NotNull UUID id, boolean darkensSky, boolean playsBossMusic, boolean createsFog);

	/**
	 * Reports that the session's connection ended, which removes every boss bar it showed.
	 */
	void closed();
}
