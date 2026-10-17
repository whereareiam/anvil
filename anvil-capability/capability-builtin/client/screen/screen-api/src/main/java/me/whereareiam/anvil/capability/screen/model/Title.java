package me.whereareiam.anvil.capability.screen.model;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A title on the player's screen, flattened to plain text, together with the subtitle shown under it and the
 * display times in effect when it appeared. A title is recorded when its text arrives and again when a subtitle
 * arrives while it is still shown, so a title whose subtitle the server sends afterwards is recorded first without
 * and then with that subtitle.
 */
@Value
@Builder
public class Title {
	/**
	 * The title's plain text.
	 */
	@NotNull String title;
	/**
	 * The subtitle's plain text, or an empty string when the server set none.
	 */
	@NotNull String subtitle;
	/**
	 * Ticks the title takes to fade in.
	 */
	int fadeInTicks;
	/**
	 * Ticks the title stays fully visible.
	 */
	int stayTicks;
	/**
	 * Ticks the title takes to fade out.
	 */
	int fadeOutTicks;

	/**
	 * Tells whether the title or the subtitle contains the supplied text.
	 *
	 * @param text required content
	 * @return whether either line contains it
	 */
	public boolean contains(@NotNull String text) {
		return title.contains(text) || subtitle.contains(text);
	}
}
