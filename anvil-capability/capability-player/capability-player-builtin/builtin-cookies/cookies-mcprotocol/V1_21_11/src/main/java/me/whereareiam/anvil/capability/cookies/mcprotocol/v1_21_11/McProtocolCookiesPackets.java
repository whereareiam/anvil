package me.whereareiam.anvil.capability.cookies.mcprotocol.v1_21_11;

import me.whereareiam.anvil.capability.cookies.packet.CookiesPacketListener;
import me.whereareiam.anvil.capability.cookies.packet.CookiesPackets;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.DisconnectedEvent;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.event.session.SessionListener;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundStoreCookiePacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.clientbound.ClientboundCookieRequestPacket;
import org.geysermc.mcprotocollib.protocol.packet.cookie.serverbound.ServerboundCookieResponsePacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cookie packets of MCProtocolLib releases from Minecraft 1.21.11, which move the request and the response into
 * their own cookie package. The request packet serves the login, configuration and game phases, so one response
 * answers a request in any of them.
 */
public final class McProtocolCookiesPackets implements CookiesPackets<Session> {
	@Override
	public @NotNull Class<Session> sessionType() {
		return Session.class;
	}

	@Override
	public void answer(@NotNull Session session, @NotNull String key, byte @Nullable [] payload) {
		session.send(new ServerboundCookieResponsePacket(Key.key(key), payload));
	}

	@Override
	public @NotNull Runnable listen(@NotNull Session session, @NotNull CookiesPacketListener listener) {
		SessionListener cookies = new SessionAdapter() {
			@Override
			public void packetReceived(Session received, Packet packet) {
				if (packet instanceof ClientboundCookieRequestPacket request) listener.requested(request.getKey().asString());
				if (packet instanceof ClientboundStoreCookiePacket store) listener.stored(store.getKey().asString(), store.getPayload());
			}

			@Override
			public void disconnected(DisconnectedEvent event) {
				listener.closed();
			}
		};
		session.addListener(cookies);
		return () -> session.removeListener(cookies);
	}
}
