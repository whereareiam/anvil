package me.whereareiam.anvil.capability.screen.packet;

import org.jetbrains.annotations.NotNull;

/**
 * Receives the title packets of one release's session, translated by {@link ScreenPackets} into plain text and
 * ticks. Each method reports one packet as the server sent it; the screen binding keeps what the client shows from
 * them.
 *
 * <pre>{@code
 * Runnable detach = packets.listenTitles(session, new TitlePacketListener() {
 *     public void title(String text) { display.title(text); }
 *     public void subtitle(String text) { display.subtitle(text); }
 *     // ...
 * });
 * }</pre>
 */
public interface TitlePacketListener {
	/**
	 * Reports a title text, which shows the title with the current subtitle.
	 *
	 * @param text title as plain text
	 */
	void title(@NotNull String text);

	/**
	 * Reports a subtitle text, shown under the current title or the next one.
	 *
	 * @param text subtitle as plain text
	 */
	void subtitle(@NotNull String text);

	/**
	 * Reports the times of the current and the following titles.
	 *
	 * @param fadeInTicks ticks a title takes to fade in
	 * @param stayTicks ticks a title stays fully visible
	 * @param fadeOutTicks ticks a title takes to fade out
	 */
	void times(int fadeInTicks, int stayTicks, int fadeOutTicks);

	/**
	 * Reports that the server cleared the title and the subtitle.
	 *
	 * @param resetTimes whether the title times return to their defaults as well
	 */
	void cleared(boolean resetTimes);
}
