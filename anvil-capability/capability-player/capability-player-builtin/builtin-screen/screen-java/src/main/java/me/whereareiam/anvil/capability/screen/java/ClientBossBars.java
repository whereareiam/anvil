package me.whereareiam.anvil.capability.screen.java;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.screen.channel.ScreenChannel;
import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.model.BossBarEvent;
import me.whereareiam.anvil.capability.screen.type.BossBarChange;
import me.whereareiam.anvil.capability.screen.type.BossBarColor;
import me.whereareiam.anvil.capability.screen.type.BossBarOverlay;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventAdd;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventRemove;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateName;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateProgress;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateProperties;
import me.whereareiam.anvil.protocol.java.api.model.screen.BossEventUpdateStyle;
import me.whereareiam.anvil.protocol.java.api.packet.screen.ClientboundBossEvent;
import me.whereareiam.anvil.protocol.java.api.type.BossEventColor;
import me.whereareiam.anvil.protocol.java.api.type.BossEventOverlay;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The boss bars a player's current connection shows, in the order they were added, kept whole from the packets that
 * each change one part of a bar, and every change emitted with the whole bar. The bars belong to one connection: when
 * it ends, every bar still shown is emitted as removed. A change of a bar the server never added is ignored, as a
 * client ignores it.
 *
 * <p>The packets of a connection arrive on its packet thread and the end of a connection clears the bars from the
 * worker, so every access holds the monitor.</p>
 */
@RequiredArgsConstructor
final class ClientBossBars {
	private final @NotNull PlayerBindingContext<Object> player;
	private final Map<UUID, BossBar> bars = new LinkedHashMap<>();

	/**
	 * Applies one boss event packet of the current connection.
	 *
	 * @param event the packet
	 */
	void apply(@NotNull ClientboundBossEvent event) {
		UUID id = event.getId();
		switch (event.getOperation()) {
			case BossEventAdd add -> add(bar(id, add));
			case BossEventRemove ignored -> remove(id);
			case BossEventUpdateProgress update -> update(id, bar -> bar.toBuilder().progress(update.getProgress()).build());
			case BossEventUpdateName update -> update(id, bar -> bar.toBuilder().text(update.getName()).build());
			case BossEventUpdateStyle update -> update(id, bar -> bar.toBuilder()
					.color(color(update.getColor()))
					.overlay(overlay(update.getOverlay()))
					.build());
			case BossEventUpdateProperties update -> update(id, bar -> bar.toBuilder()
					.darkensSky(update.isDarkenScreen())
					.playsBossMusic(update.isPlayMusic())
					.createsFog(update.isCreateWorldFog())
					.build());
		}
	}

	/**
	 * Removes every bar still shown and emits each as removed.
	 */
	void removeAll() {
		for (BossBar bar : clear()) emit(BossBarChange.REMOVED, bar);
	}

	/**
	 * Forgets every bar still shown without emitting anything, for a player whose screen is no longer observed.
	 */
	void discard() {
		clear();
	}

	private synchronized List<BossBar> clear() {
		List<BossBar> removed = new ArrayList<>(bars.values());
		bars.clear();

		return removed;
	}

	private void add(BossBar bar) {
		synchronized (this) {
			bars.put(bar.getId(), bar);
		}
		emit(BossBarChange.ADDED, bar);
	}

	private void remove(UUID id) {
		BossBar removed;
		synchronized (this) {
			removed = bars.remove(id);
		}
		if (removed != null) emit(BossBarChange.REMOVED, removed);
	}

	private void update(UUID id, UnaryOperator<BossBar> change) {
		BossBar updated;
		synchronized (this) {
			updated = bars.computeIfPresent(id, (ignored, bar) -> change.apply(bar));
		}
		if (updated != null) emit(BossBarChange.UPDATED, updated);
	}

	private void emit(BossBarChange change, BossBar bar) {
		player.emit(ScreenChannel.BOSS_BAR, new BossBarEvent(change, bar));
	}

	private static BossBar bar(UUID id, BossEventAdd add) {
		return BossBar.builder()
				.id(id)
				.text(add.getName())
				.progress(add.getProgress())
				.color(color(add.getColor()))
				.overlay(overlay(add.getOverlay()))
				.darkensSky(add.isDarkenScreen())
				.playsBossMusic(add.isPlayMusic())
				.createsFog(add.isCreateWorldFog())
				.build();
	}

	private static BossBarColor color(BossEventColor color) {
		return switch (color) {
			case PINK -> BossBarColor.PINK;
			case BLUE -> BossBarColor.BLUE;
			case RED -> BossBarColor.RED;
			case GREEN -> BossBarColor.GREEN;
			case YELLOW -> BossBarColor.YELLOW;
			case PURPLE -> BossBarColor.PURPLE;
			case WHITE -> BossBarColor.WHITE;
		};
	}

	private static BossBarOverlay overlay(BossEventOverlay overlay) {
		return switch (overlay) {
			case PROGRESS -> BossBarOverlay.PROGRESS;
			case NOTCHED_6 -> BossBarOverlay.NOTCHED_6;
			case NOTCHED_10 -> BossBarOverlay.NOTCHED_10;
			case NOTCHED_12 -> BossBarOverlay.NOTCHED_12;
			case NOTCHED_20 -> BossBarOverlay.NOTCHED_20;
		};
	}
}
