package me.whereareiam.anvil.capability.sounds;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import me.whereareiam.anvil.capability.sounds.packet.SoundsPackets;
import org.jetbrains.annotations.NotNull;

/**
 * Worker-side sounds behavior over the packets of one library release: it emits each sound a native session hears
 * while it is still the player's current one.
 *
 * @param <S> native session type of the library release
 */
@RequiredArgsConstructor
public final class SoundsBinding<S> {
	private final @NotNull SoundsPackets<S> packets;

	/**
	 * Observes every native session the player connects with. Sounds register no operations.
	 *
	 * @param player native lifecycle, session access and event delivery of the player
	 * @param operations typed operation registration for the player
	 * @return binding that removes the native listener of the current session when the player is destroyed
	 */
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		Subscription heard = player.bindNativeSession(nativeSession -> listen(player, nativeSession));
		return heard::close;
	}

	private Subscription listen(PlayerBindingContext<Object> player, Object nativeSession) {
		Runnable detach = packets.listen(packets.sessionType().cast(nativeSession), sound -> {
			if (player.isCurrentNativeSession(nativeSession)) player.emit(SoundsOperations.HEARD, sound);
		});
		return detach::run;
	}
}
