package me.whereareiam.anvil.capability.screen;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.model.BossBarEvent;
import me.whereareiam.anvil.capability.screen.model.Title;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Observes what the server draws over the player's game view besides chat: titles with their subtitles, action
 * bars and boss bars.
 *
 * <p>Each kind has its own history, kept in order and across reconnects, and its own watch, which follows only
 * what arrives after it was created. Text is flattened to plain text as {@code Messages} flattens chat. Action bars
 * are the text the server sends through the separate action-bar packet; action-bar text sent as chat, which is
 * game-info chat before Minecraft 1.19 and overlay system chat from 1.19, belongs to the {@code Messages} history
 * instead.</p>
 *
 * <pre>{@code
 * Screen screen = alice.capability(Screen.class);
 * TitleWatch titles = screen.watchTitles();
 * BossBarWatch bars = screen.watchBossBars();
 * messages.command("event start");
 * titles.shown("Event started");
 * BossBar event = bars.shown(bar -> bar.getText().contains("Event") && bar.getProgress() == 1F, timeout);
 * }</pre>
 */
public interface Screen extends PlayerCapability {
	/**
	 * Returns every title shown so far, without waiting.
	 *
	 * @return immutable title history, oldest first
	 */
	@NotNull List<Title> titles();

	/**
	 * Returns every action bar shown so far, as plain text, without waiting.
	 *
	 * @return immutable action-bar history, oldest first
	 */
	@NotNull List<String> actionBars();

	/**
	 * Returns every boss bar change so far, without waiting. A connection that ends removes the bars it showed, so
	 * the history records each of them as removed.
	 *
	 * @return immutable boss bar history, oldest first
	 */
	@NotNull List<BossBarEvent> bossBarEvents();

	/**
	 * Returns the boss bars on the player's screen now, without waiting.
	 *
	 * @return immutable list of shown bars in the order they were added
	 */
	@NotNull List<BossBar> bossBars();

	/**
	 * Starts following the titles shown from now on. Each watch keeps its own position.
	 *
	 * <pre>{@code
	 * TitleWatch titles = screen.watchTitles();
	 * messages.command("example announce");
	 * titles.shown(title -> title.getTitle().contains("Announcement") && title.getSubtitle().contains("Alice"), timeout);
	 * }</pre>
	 *
	 * @return a watch that starts after the last title shown so far
	 */
	@NotNull TitleWatch watchTitles();

	/**
	 * Starts following the action bars shown from now on. Each watch keeps its own position.
	 *
	 * <pre>{@code
	 * ActionBarWatch bar = screen.watchActionBars();
	 * messages.command("follow Bob");
	 * bar.shown("+1 follower");
	 * }</pre>
	 *
	 * @return a watch that starts after the last action bar shown so far
	 */
	@NotNull ActionBarWatch watchActionBars();

	/**
	 * Starts following the boss bar changes from now on. Each watch keeps its own position.
	 *
	 * <pre>{@code
	 * BossBarWatch bars = screen.watchBossBars();
	 * BossBar trending = bars.shown("Trending");
	 * bars.removed(bar -> bar.getId().equals(trending.getId()), timeout);
	 * }</pre>
	 *
	 * @return a watch that starts after the last boss bar change so far
	 */
	@NotNull BossBarWatch watchBossBars();
}
