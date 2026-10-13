package me.whereareiam.anvil.capability.commands.packet;

import me.whereareiam.anvil.capability.commands.model.CommandTree;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Reads the command trees of one protocol-library release. This is the port a library segment implements and
 * declares in {@code META-INF/services/me.whereareiam.anvil.capability.commands.packet.CommandsPackets}: the worker
 * selects the segment for the player's release and hands its implementation to the commands wiring, and the
 * library-neutral commands binding owns everything else, such as which native connection generation a tree belongs
 * to.
 *
 * <p>Implementations are stateless. A parser's ID is its vanilla ID: {@code brigadier:} for the Brigadier types and
 * {@code minecraft:} for the others. Releases before Minecraft 1.21.6 do not mark nodes that need permissions.</p>
 *
 * <pre>{@code
 * public final class McProtocolCommandsPackets implements CommandsPackets<Session> {
 *     public Class<Session> sessionType() { return Session.class; }
 *     public Runnable listen(Session session, Consumer<CommandTree> trees) {
 *         SessionListener listener = new SessionAdapter() {
 *             public void packetReceived(Session received, Packet packet) {
 *                 if (session.isConnected() && packet instanceof ClientboundCommandsPacket commands) trees.accept(tree(commands));
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
public interface CommandsPackets<S> {
	/**
	 * Returns the native session type the packets are received through. The binding casts the player's native
	 * session with it, so a session interface of the release may be returned.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Reports every command tree the session receives while it is connected, until the returned handle runs.
	 * Trees arrive on the library's network thread in the order the session receives them.
	 *
	 * @param session the native session to observe
	 * @param trees receives each tree
	 * @return handle that removes the native listener; running it again has no effect
	 */
	@NotNull Runnable listen(@NotNull S session, @NotNull Consumer<CommandTree> trees);
}
