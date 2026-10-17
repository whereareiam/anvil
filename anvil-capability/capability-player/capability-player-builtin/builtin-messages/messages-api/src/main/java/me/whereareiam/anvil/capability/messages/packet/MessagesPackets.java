package me.whereareiam.anvil.capability.messages.packet;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Sends the chat and command packets of one protocol-library release and reports the messages the player receives
 * as plain text. This is the port a library segment implements and declares in
 * {@code META-INF/services/me.whereareiam.anvil.capability.messages.packet.MessagesPackets}: the worker selects the
 * segment for the player's release and hands its implementation to the messages wiring, and the library-neutral
 * messages binding owns everything else, such as which native connection generation a received message belongs to.
 *
 * <p>Implementations are stateless and are called only with the player's current native session. Plain text is the
 * release's message component flattened in order: the content of each text part, the key name of each keybind part,
 * the pattern of each selector part and, for a translated part, its translation key followed by each argument after
 * a space, so a vanilla chat line such as {@code chat.type.text} keeps the sender and the message. Score, NBT and
 * object parts contribute nothing.</p>
 *
 * <pre>{@code
 * public final class McProtocolMessagesPackets implements MessagesPackets<Session> {
 *     public Class<Session> sessionType() { return Session.class; }
 *     public void chat(Session session, String text) { session.send(new ServerboundChatPacket(text)); }
 *     public void command(Session session, String commandWithoutSlash) { chat(session, "/" + commandWithoutSlash); }
 *     public Runnable listen(Session session, Consumer<String> plainText) {
 *         SessionListener listener = new SessionAdapter() {
 *             public void packetReceived(Session received, Packet packet) {
 *                 // flatten(Component) appends text contents, and translation keys with their arguments, in order
 *                 if (session.isConnected() && packet instanceof ClientboundChatPacket chat)
 *                     plainText.accept(flatten(chat.getMessage()));
 *             }
 *         };
 *         session.addListener(listener);
 *         return () -> session.removeListener(listener);
 *     }
 * }
 * }</pre>
 *
 * @param <S> native session type of the library release, or a supertype the session implements
 */
public interface MessagesPackets<S> {
	/**
	 * Returns the native session type the packets are sent and received through. The binding casts the player's
	 * native session with it, so a session interface of the release may be returned.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Sends a public chat message, unsigned where the release signs chat. Releases without a command packet send the
	 * text unchanged, so their servers run text starting with a slash as a command.
	 *
	 * @param session the player's current native session
	 * @param text chat text without a command prefix
	 */
	void chat(@NotNull S session, @NotNull String text);

	/**
	 * Sends a server command, unsigned where the release signs commands. Releases without a command packet send it
	 * as chat with a leading slash.
	 *
	 * @param session the player's current native session
	 * @param commandWithoutSlash command and arguments without the leading slash
	 */
	void command(@NotNull S session, @NotNull String commandWithoutSlash);

	/**
	 * Reports every chat message the session receives while it is connected, flattened to plain text, until the
	 * returned handle runs: player and system chat, including the game-info or overlay chat a client shows above
	 * the hotbar, and, in releases that have it, disguised chat. Messages arrive on the library's network thread in
	 * the order the session receives them.
	 *
	 * @param session the native session to observe
	 * @param plainText receives each message as plain text
	 * @return handle that removes the native listener; running it again has no effect
	 */
	@NotNull Runnable listen(@NotNull S session, @NotNull Consumer<String> plainText);
}
