package me.whereareiam.anvil.capability.screen.java;

import me.whereareiam.anvil.capability.screen.channel.ScreenChannel;
import me.whereareiam.anvil.capability.screen.model.Title;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundClearTitles;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetSubtitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitleText;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundSetTitlesAnimation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.LongSupplier;

/**
 * The title, subtitle and title times one connection shows, kept from its title packets as a client keeps them, and
 * each title it shows emitted. A title stays for the sum of its times from when its text arrived, measured on the
 * worker's clock as a client measures it in ticks; once that has passed, the title and the subtitle are gone. A
 * subtitle shows only together with a title, so one that arrives while no title is shown waits for the next.
 *
 * <p>Only the packet thread of its connection calls it.</p>
 */
final class ClientTitles {
	private static final int DEFAULT_FADE_IN_TICKS = 10;
	private static final int DEFAULT_STAY_TICKS = 70;
	private static final int DEFAULT_FADE_OUT_TICKS = 20;
	private static final long TICK_NANOS = 50_000_000L;

	private final PlayerBindingContext<Object> player;
	private final LongSupplier nanoClock;
	private @Nullable String title;
	private @Nullable String subtitle;
	private long shownUntil;
	private int fadeInTicks = DEFAULT_FADE_IN_TICKS;
	private int stayTicks = DEFAULT_STAY_TICKS;
	private int fadeOutTicks = DEFAULT_FADE_OUT_TICKS;

	ClientTitles(@NotNull PlayerBindingContext<Object> player, @NotNull LongSupplier nanoClock) {
		this.player = player;
		this.nanoClock = nanoClock;
	}

	void title(@NotNull ClientboundSetTitleText packet) {
		expire();
		title = packet.getText();
		shownUntil = nanoClock.getAsLong() + displayNanos();
		player.emit(ScreenChannel.TITLE, shown(title));
	}

	void subtitle(@NotNull ClientboundSetSubtitleText packet) {
		expire();
		subtitle = packet.getText();
		if (title != null) player.emit(ScreenChannel.TITLE, shown(title));
	}

	/**
	 * Sets the times a negative value leaves unchanged and restarts a title still shown with them.
	 */
	void times(@NotNull ClientboundSetTitlesAnimation packet) {
		expire();
		if (packet.getFadeIn() >= 0) fadeInTicks = packet.getFadeIn();
		if (packet.getStay() >= 0) stayTicks = packet.getStay();
		if (packet.getFadeOut() >= 0) fadeOutTicks = packet.getFadeOut();
		if (title != null) shownUntil = nanoClock.getAsLong() + displayNanos();
	}

	void cleared(@NotNull ClientboundClearTitles packet) {
		title = null;
		subtitle = null;
		if (!packet.isResetTimes()) return;

		fadeInTicks = DEFAULT_FADE_IN_TICKS;
		stayTicks = DEFAULT_STAY_TICKS;
		fadeOutTicks = DEFAULT_FADE_OUT_TICKS;
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
