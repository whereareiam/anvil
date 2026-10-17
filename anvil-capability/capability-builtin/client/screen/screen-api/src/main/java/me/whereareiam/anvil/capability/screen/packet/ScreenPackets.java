package me.whereareiam.anvil.capability.screen.packet;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Reports the title, action-bar and boss bar packets of one protocol-library release, each kind to its own listener.
 * This is the port a library segment implements and declares in
 * {@code META-INF/services/me.whereareiam.anvil.capability.screen.packet.ScreenPackets}: the worker selects the segment
 * for the player's release and hands its implementation to the screen wiring, and the library-neutral screen binding
 * owns everything else, such as how long a title stays and which native connection generation a packet belongs to.
 *
 * <p>Implementations are stateless. Every listener receives packets on the library's network thread, in the order
 * the session receives them, and only while the session is connected. Text is the release's component flattened in
 * order, as the messages port flattens chat: the content of each text part, the key name of each keybind part, the
 * pattern of each selector part and, for a translated part, its translation key followed by each argument after a
 * space. Score, NBT and object parts contribute nothing.</p>
 *
 * <pre>{@code
 * public final class McProtocolScreenPackets implements ScreenPackets<Session> {
 *     public Class<Session> sessionType() { return Session.class; }
 *     public Runnable listenActionBars(Session session, Consumer<String> actionBars) {
 *         SessionListener listener = new SessionAdapter() {
 *             public void packetReceived(Session received, Packet packet) {
 *                 if (session.isConnected() && packet instanceof ClientboundSetActionBarTextPacket actionBar)
 *                     actionBars.accept(flatten(actionBar.getText()));
 *             }
 *         };
 *         session.addListener(listener);
 *         return () -> session.removeListener(listener);
 *     }
 *     // listenTitles and listenBossBars likewise
 * }
 * }</pre>
 *
 * @param <S> native session type of the library release, or a supertype the session implements
 */
public interface ScreenPackets<S> {
	/**
	 * Returns the native session type the packets are received through. The binding casts the player's native
	 * session with it, so a session interface of the release may be returned.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Reports every title, subtitle, title-times and title-clear packet the session receives until the returned
	 * handle runs.
	 *
	 * @param session the native session to observe
	 * @param listener receives each title packet
	 * @return handle that removes the native listener; running it again has no effect
	 */
	@NotNull Runnable listenTitles(@NotNull S session, @NotNull TitlePacketListener listener);

	/**
	 * Reports the text of every action-bar packet the session receives until the returned handle runs. Action-bar
	 * text the server sends as chat is not reported.
	 *
	 * @param session the native session to observe
	 * @param actionBars receives each action bar as plain text
	 * @return handle that removes the native listener; running it again has no effect
	 */
	@NotNull Runnable listenActionBars(@NotNull S session, @NotNull Consumer<String> actionBars);

	/**
	 * Reports every boss bar packet the session receives, and the end of its connection, until the returned handle
	 * runs.
	 *
	 * @param session the native session to observe
	 * @param listener receives each boss bar packet
	 * @return handle that removes the native listener; running it again has no effect
	 */
	@NotNull Runnable listenBossBars(@NotNull S session, @NotNull BossBarPacketListener listener);
}
