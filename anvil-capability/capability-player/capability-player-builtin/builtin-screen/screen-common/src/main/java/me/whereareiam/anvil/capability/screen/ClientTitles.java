package me.whereareiam.anvil.capability.screen;

import me.whereareiam.anvil.capability.screen.model.Title;
import me.whereareiam.anvil.capability.screen.packet.TitlePacketListener;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongSupplier;

/**
 * The title, subtitle and title times one connection shows, kept from its title packets as a client keeps them, and
 * each title it shows emitted while the connection is the player's current one. A title stays for the sum of its
 * times from when its text arrived, measured on the worker's clock as a client measures it in ticks; once that has
 * passed, the title and the subtitle are gone. A subtitle shows only together with a title, so one that arrives while
 * no title is shown waits for the next.
 *
 * <p>Only the network thread of its connection calls it.</p>
 */
final class ClientTitles implements TitlePacketListener {
	private static final int DEFAULT_FADE_IN_TICKS = 10;
	private static final int DEFAULT_STAY_TICKS = 70;
	private static final int DEFAULT_FADE_OUT_TICKS = 20;
	private static final long TICK_NANOS = 50_000_000L;

	private final PlayerBindingContext<Object> player;
	private final Object nativeSession;
	private final LongSupplier nanoClock;
	private @Nullable String title;
	private @Nullable String subtitle;
	private long shownUntil;
	private int fadeInTicks = DEFAULT_FADE_IN_TICKS;
	private int stayTicks = DEFAULT_STAY_TICKS;
	private int fadeOutTicks = DEFAULT_FADE_OUT_TICKS;

	ClientTitles(@NotNull PlayerBindingContext<Object> player, @NotNull Object nativeSession, @NotNull LongSupplier nanoClock) {
		this.player = player;
		this.nativeSession = nativeSession;
		this.nanoClock = nanoClock;
	}

	@Override
	public void title(@NotNull String text) {
		if (!current()) return;

		expire();
		title = text;
		shownUntil = nanoClock.getAsLong() + displayNanos();
		player.emit(ScreenOperations.TITLE, shown(text));
	}

	@Override
	public void subtitle(@NotNull String text) {
		if (!current()) return;

		expire();
		subtitle = text;
		if (title != null) player.emit(ScreenOperations.TITLE, shown(title));
	}

	/**
	 * Sets the times a negative value leaves unchanged and restarts a title still shown with them.
	 */
	@Override
	public void times(int fadeIn, int stay, int fadeOut) {
		if (!current()) return;

		expire();
		if (fadeIn >= 0) fadeInTicks = fadeIn;
		if (stay >= 0) stayTicks = stay;
		if (fadeOut >= 0) fadeOutTicks = fadeOut;
		if (title != null) shownUntil = nanoClock.getAsLong() + displayNanos();
	}

	@Override
	public void cleared(boolean resetTimes) {
		if (!current()) return;

		title = null;
		subtitle = null;
		if (!resetTimes) return;

		fadeInTicks = DEFAULT_FADE_IN_TICKS;
		stayTicks = DEFAULT_STAY_TICKS;
		fadeOutTicks = DEFAULT_FADE_OUT_TICKS;
	}

	private boolean current() {
		return player.isCurrentNativeSession(nativeSession);
	}

	private void expire() {
		if (title == null || nanoClock.getAsLong() - shownUntil < 0) return;

		title = null;
		subtitle = null;
	}

	private long displayNanos() {
		return (long) (fadeInTicks + stayTicks + fadeOutTicks) * TICK_NANOS;
	}

	private Title shown(String text) {
		return Title.builder()
				.title(text)
				.subtitle(subtitle == null ? "" : subtitle)
				.fadeInTicks(fadeInTicks)
				.stayTicks(stayTicks)
				.fadeOutTicks(fadeOutTicks)
				.build();
	}
}
