package me.whereareiam.anvil.capability.cookies.java;

import me.whereareiam.anvil.capability.cookies.channel.CookiesChannel;
import me.whereareiam.anvil.protocol.api.channel.Subscription;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.api.worker.OperationRegistry;
import me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext;
import me.whereareiam.anvil.protocol.api.worker.WorkerBinding;
import me.whereareiam.anvil.protocol.api.worker.WorkerExtension;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundCookieRequest;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ClientboundStoreCookie;
import me.whereareiam.anvil.protocol.java.api.packet.cookie.ServerboundCookieResponse;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Installs the built-in cookies capability for players that speak the Java Edition protocol: it keeps the cookies of
 * each of a player's connections, answers every cookie request of the current one on the packet thread before the
 * next packet is read, and registers the operations through which the test gives and takes away cookies. Releases
 * before Minecraft 1.20.5 have no cookies, so the worker reports the capability unavailable for them.
 */
public final class JavaCookiesExtension implements WorkerExtension<Object> {
	private static final String FEATURE = "cookies";
	private static final List<Class<? extends JavaPacket>> PACKETS = List.of(
			ClientboundCookieRequest.class,
			ClientboundStoreCookie.class,
			ServerboundCookieResponse.class
	);

	@Override
	public @NotNull String id() {
		return CookiesChannel.ID;
	}

	@Override
	public @NotNull Class<Object> nativeSessionType() {
		return Object.class;
	}

	/**
	 * Installs the cookies operations and the cookie packet listeners for one player.
	 *
	 * @throws ProtocolUnavailableException when the player does not speak the Java Edition protocol or its release
	 * has no cookies
	 */
	@Override
	public @NotNull WorkerBinding bind(@NotNull PlayerBindingContext<Object> player, @NotNull OperationRegistry operations) {
		JavaProtocol protocol = player.protocol(JavaProtocol.class);
		protocol.require(FEATURE, PACKETS);

		ClientCookies cookies = new ClientCookies(player);
		operations.register(CookiesChannel.STORE, stored -> cookies.store(stored.getKey(), stored.getPayload()));
		operations.register(CookiesChannel.REMOVE, key -> cookies.remove(key.getKey()));

		Subscription connections = player.bindConnection(() -> {
			cookies.opened();
			return cookies::ended;
		});
		Subscription binding = Subscription.all(
				protocol.listen(ClientboundCookieRequest.class, request -> protocol.send(
						new ServerboundCookieResponse(request.getKey(), cookies.requested(request.getKey())))),
				protocol.listen(ClientboundStoreCookie.class, store ->
						cookies.serverStored(store.getKey(), store.getPayload())),
				connections
		);

		return binding::close;
	}
}
