package me.whereareiam.anvil.capability.dialogs.packet;

import me.whereareiam.anvil.capability.dialogs.model.click.DialogClick;
import org.jetbrains.annotations.NotNull;

/**
 * Reads the dialog-related packets of one protocol-library release and sends what the client sends when a dialog
 * button is pressed. This is the port a library segment implements and declares in
 * {@code META-INF/services/me.whereareiam.anvil.capability.dialogs.packet.DialogsPackets}: the worker selects the
 * segment for the player's release and hands its implementation to the dialogs wiring, and the library-neutral
 * dialogs binding owns everything else, such as which native connection generation a dialog belongs to. Releases
 * before Minecraft 1.21.6 have no dialogs and no segment.
 *
 * <p>Implementations are stateless and are called only with the player's current native session. Text is flattened
 * to plain text as {@link me.whereareiam.anvil.capability.dialogs.model.dialog.Dialog} describes.</p>
 *
 * <pre>{@code
 * public final class McProtocolDialogsPackets implements DialogsPackets<Session> {
 *     public Class<Session> sessionType() { return Session.class; }
 *     public void click(Session session, DialogClick click) {
 *         if (click.getCommand() != null) {
 *             session.send(new ServerboundChatCommandPacket(click.getCommand()));
 *             return;
 *         }
 *
 *         session.send(new ServerboundCustomClickActionPacket(Key.key(click.getCustomId()), payload(click)));
 *     }
 *     public Runnable listen(Session session, DialogsPacketListener listener) {
 *         SessionListener native = new SessionAdapter() {
 *             public void packetReceived(Session received, Packet packet) {
 *                 if (packet instanceof ClientboundShowDialogGamePacket show) listener.shown(dialog(show.getDialog()));
 *                 if (packet instanceof ClientboundClearDialogPacket) listener.cleared();
 *                 if (packet instanceof ClientboundRespawnPacket) listener.reset();
 *             }
 *         };
 *         session.addListener(native);
 *         return () -> session.removeListener(native);
 *     }
 * }
 * }</pre>
 *
 * @param <S> native session type of the library release, or a supertype the session implements
 */
public interface DialogsPackets<S> {
	/**
	 * Returns the native session type the packets are sent and received through. The binding casts the player's
	 * native session with it, so a session interface of the release may be returned.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Sends what the client sends for a pressed dialog button: an unsigned command, or a custom click whose payload
	 * is the click's SNBT compound with each of its values put in under its key.
	 *
	 * @param session the player's current native session
	 * @param click the command or custom click to send
	 * @throws IllegalArgumentException when the click's payload is not an SNBT compound
	 */
	void click(@NotNull S session, @NotNull DialogClick click);

	/**
	 * Reports what the session receives about dialogs while it is connected, until the returned handle runs:
	 * dialogs shown and cleared in the game and during configuration, every packet after which the client replaces
	 * its screens (joining a level, respawning and starting configuration), and the dialog registry.
	 * Reports arrive on the library's network thread in the order the session receives the packets.
	 *
	 * @param session the native session to observe
	 * @param listener receives the reports
	 * @return handle that removes the native listener; running it again has no effect
	 */
	@NotNull Runnable listen(@NotNull S session, @NotNull DialogsPacketListener listener);
}
