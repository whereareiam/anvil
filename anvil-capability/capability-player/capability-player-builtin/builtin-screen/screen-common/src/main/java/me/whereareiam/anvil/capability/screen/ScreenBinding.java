package me.whereareiam.anvil.capability.screen;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.screen.packet.ScreenPackets;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;

import java.util.function.LongSupplier;

/**
 * Worker-side screen behavior over the packets of one library release: for each native session of a player it
 * listens to the title packets through {@link ClientTitles}, to the action-bar packets, and to the boss bar packets
 * through the player's {@link ClientBossBars}, which emit what the session shows while it is the player's current
 * one. Each connection starts without a title and without boss bars.
 *
 * @param <S> native session type of the library release
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class ScreenBinding<S> {
	private final @NotNull ScreenPackets<S> packets;
	private final @NotNull LongSupplier nanoClock;

	/**
	 * Creates the binding over the packets of the player's release, timing titles on the system clock.
	 *
	 * @param packets the release's screen packets
	 */
	public ScreenBinding(@NotNull ScreenPackets<S> packets) {
		this(packets, System::nanoTime);
	}

	/**
	 * Observes every native session the player connects with. The screen registers no operations.
	 *
	 * @param player native lifecycle, session access and event delivery of the player
	 * @param operations typed operation registration for the player
	 * @return binding that removes the native listeners of the current session when the player is destroyed
	 */
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		ClientBossBars bossBars = new ClientBossBars(player);
		Subscription listening = player.bindNativeSession(nativeSession -> listen(player, bossBars, nativeSession));
		return listening::close;
	}

	private Subscription listen(PlayerBindingContext<Object> player, ClientBossBars bossBars, Object nativeSession) {
		S session = packets.sessionType().cast(nativeSession);
		bossBars.removeAll();
		Runnable titles = packets.listenTitles(session, new ClientTitles(player, nativeSession, nanoClock));
		Runnable actionBars = packets.listenActionBars(session, text -> {
			if (player.isCurrentNativeSession(nativeSession)) player.emit(ScreenOperations.ACTION_BAR, text);
		});
		Runnable bars = packets.listenBossBars(session, bossBars.listener(nativeSession));
		return () -> {
			titles.run();
			actionBars.run();
			bars.run();
		};
	}
}
