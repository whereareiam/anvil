package me.whereareiam.anvil.capability.screen;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.screen.model.BossBar;
import me.whereareiam.anvil.capability.screen.model.BossBarEvent;
import me.whereareiam.anvil.capability.screen.packet.BossBarPacketListener;
import me.whereareiam.anvil.capability.screen.type.BossBarChange;
import me.whereareiam.anvil.capability.screen.type.BossBarColor;
import me.whereareiam.anvil.capability.screen.type.BossBarOverlay;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * The boss bars a player's connection shows, in the order they were added, kept whole from the packets that each
 * change one part of a bar, and every change emitted with the whole bar. The bars belong to one connection: when it
 * ends, or a new one starts without its end having been reported, every bar still shown is emitted as removed. A
 * change of a bar the server never added is ignored, as a client ignores it.
 *
 * <p>The packets of a connection arrive on its network thread and a new connection clears the bars from the worker,
 * so every access holds the monitor.</p>
 */
@RequiredArgsConstructor
final class ClientBossBars {
	private final @NotNull PlayerBindingContext<Object> player;
	private final Map<UUID, BossBar> bars = new LinkedHashMap<>();

	/**
	 * Returns the listener for the boss bar packets of one native session, which applies them only while that session
	 * is the player's current one.
	 *
	 * @param nativeSession the session the packets arrive on
	 * @return listener of that session
	 */
	@NotNull BossBarPacketListener listener(@NotNull Object nativeSession) {
		return new SessionBossBars(nativeSession);
	}

	/**
	 * Removes every bar still shown and emits each as removed.
	 */
	void removeAll() {
		List<BossBar> removed;
		synchronized (this) {
			removed = new ArrayList<>(bars.values());
			bars.clear();
		}
		for (BossBar bar : removed) emit(BossBarChange.REMOVED, bar);
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
		player.emit(ScreenOperations.BOSS_BAR, new BossBarEvent(change, bar));
	}

	/**
	 * Applies the boss bar packets of one native session while it is the player's current one.
	 */
	@RequiredArgsConstructor
	private final class SessionBossBars implements BossBarPacketListener {
		private final Object nativeSession;

		@Override
		public void added(@NotNull BossBar bar) {
			if (current()) add(bar);
		}

		@Override
		public void removed(@NotNull UUID id) {
			if (current()) remove(id);
		}

		@Override
		public void progress(@NotNull UUID id, float progress) {
			changed(id, bar -> bar.toBuilder().progress(progress).build());
		}

		@Override
		public void text(@NotNull UUID id, @NotNull String text) {
			changed(id, bar -> bar.toBuilder().text(text).build());
		}

		@Override
		public void style(@NotNull UUID id, @NotNull BossBarColor color, @NotNull BossBarOverlay overlay) {
			changed(id, bar -> bar.toBuilder().color(color).overlay(overlay).build());
		}

		@Override
		public void flags(@NotNull UUID id, boolean darkensSky, boolean playsBossMusic, boolean createsFog) {
			changed(id, bar -> bar.toBuilder().darkensSky(darkensSky).playsBossMusic(playsBossMusic).createsFog(createsFog).build());
		}

		@Override
		public void closed() {
			if (current()) removeAll();
		}

		private void changed(UUID id, UnaryOperator<BossBar> change) {
			if (current()) update(id, change);
		}

		private boolean current() {
			return player.isCurrentNativeSession(nativeSession);
		}
	}
}
