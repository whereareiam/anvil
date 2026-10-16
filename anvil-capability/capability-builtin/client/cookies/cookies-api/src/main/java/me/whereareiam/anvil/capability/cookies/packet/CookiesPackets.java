package me.whereareiam.anvil.capability.cookies.packet;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Reads the cookie packets of one protocol-library release and answers cookie requests. This is the port a library
 * segment implements and declares in
 * {@code META-INF/services/me.whereareiam.anvil.capability.cookies.packet.CookiesPackets}: the worker selects the
 * segment for the player's release and hands its implementation to the cookies wiring, and the library-neutral
 * cookies binding owns everything else, such as which cookies each connection holds. Releases before Minecraft
 * 1.20.5 have no cookies and no segment.
 *
 * <p>Implementations are stateless and are called only with the player's native sessions. Keys are resource
 * locations with their namespace, such as {@code minecraft:ticket}.</p>
 *
 * <pre>{@code
 * public final class McProtocolCookiesPackets implements CookiesPackets<Session> {
 *     public Class<Session> sessionType() { return Session.class; }
 *     public void answer(Session session, String key, byte[] payload) {
 *         session.send(new ServerboundCookieResponsePacket(Key.key(key), payload));
 *     }
 *     public Runnable listen(Session session, CookiesPacketListener listener) {
 *         SessionListener cookies = new SessionAdapter() {
 *             public void packetReceived(Session received, Packet packet) {
 *                 if (packet instanceof ClientboundCookieRequestPacket request) listener.requested(request.getKey().asString());
 *                 if (packet instanceof ClientboundStoreCookiePacket store) listener.stored(store.getKey().asString(), store.getPayload());
 *             }
 *             public void disconnected(DisconnectedEvent event) { listener.closed(); }
 *         };
 *         session.addListener(cookies);
 *         return () -> session.removeListener(cookies);
 *     }
 * }
 * }</pre>
 *
 * @param <S> native session type of the library release, or a supertype the session implements
 */
public interface CookiesPackets<S> {
	/**
	 * Returns the native session type the packets are sent and received through. The binding casts the player's
	 * native session with it, so a session interface of the release may be returned.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Answers a cookie request through the packet of the phase the session is in.
	 *
	 * @param session the native session the request arrived on
	 * @param key requested key, with its namespace
	 * @param payload the stored payload, or {@code null} when the client holds no cookie under the key
	 */
	void answer(@NotNull S session, @NotNull String key, byte @Nullable [] payload);

	/**
	 * Reports every cookie request and stored cookie the session receives, and the end of its connection, until the
	 * returned handle runs. Calls arrive on the library's network thread in the order the session receives the
	 * packets, so a request is answered before the session reads the next packet.
	 *
	 * @param session the native session to observe
	 * @param listener receives the session's cookie packets
	 * @return handle that removes the native listener; running it again has no effect
	 */
	@NotNull Runnable listen(@NotNull S session, @NotNull CookiesPacketListener listener);
}
