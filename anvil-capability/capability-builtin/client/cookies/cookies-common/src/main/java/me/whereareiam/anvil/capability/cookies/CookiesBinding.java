package me.whereareiam.anvil.capability.cookies;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.channel.OperationRegistry;
import me.whereareiam.anvil.capability.cookies.packet.CookiesPacketListener;
import me.whereareiam.anvil.capability.cookies.packet.CookiesPackets;
import me.whereareiam.anvil.capability.protocol.api.player.channel.Subscription;
import me.whereareiam.anvil.capability.protocol.api.player.worker.PlayerBindingContext;
import me.whereareiam.anvil.capability.protocol.api.player.worker.WorkerBinding;
import org.jetbrains.annotations.NotNull;

/**
 * Worker-side cookies behavior over the packets of one library release: it keeps the cookies of each of a player's
 * connections, answers every cookie request of the current one on the network thread before the next packet is read,
 * and registers the operations through which the test gives and takes away cookies.
 *
 * @param <S> native session type of the library release
 */
@RequiredArgsConstructor
public final class CookiesBinding<S> {
	private final @NotNull CookiesPackets<S> packets;

	/**
	 * Installs the cookies operations for one player and observes every native session the player connects with.
	 *
	 * @param player native lifecycle, session access and event delivery of the player
	 * @param operations typed operation registration for the player
	 * @return binding that removes the native listener of the current session when the player is destroyed
	 */
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		ClientCookies cookies = new ClientCookies(player);
		operations.register(CookiesOperations.STORE, stored -> cookies.store(stored.getKey(), stored.getPayload()));
		operations.register(CookiesOperations.REMOVE, key -> cookies.remove(key.getKey()));

		Subscription listening = player.bindNativeSession(nativeSession -> listen(cookies, nativeSession));
		return listening::close;
	}

	private Subscription listen(ClientCookies cookies, Object nativeSession) {
		S session = packets.sessionType().cast(nativeSession);
		cookies.opened(nativeSession);
		Runnable detach = packets.listen(session, new CookiesPacketListener() {
			@Override
			public void requested(@NotNull String key) {
				packets.answer(session, key, cookies.requested(nativeSession, key));
			}

			@Override
			public void stored(@NotNull String key, byte @NotNull [] payload) {
				cookies.serverStored(nativeSession, key, payload);
			}

			@Override
			public void closed() {
				cookies.closed(nativeSession);
			}
		});
		return detach::run;
	}
}
