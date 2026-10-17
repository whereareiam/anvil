package me.whereareiam.anvil.capability.dialogs.packet;

import me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtCompound;
import org.jetbrains.annotations.NotNull;

/**
 * Reads the dialog-related packets of one protocol-library release and sends what the client sends when a dialog
 * button is pressed. This is the port a library segment implements and declares in
 * {@code META-INF/services/me.whereareiam.anvil.capability.dialogs.packet.DialogsPackets}: the worker selects the
 * segment for the player's release and hands its implementation to the dialogs wiring. Releases before Minecraft
 * 1.21.6 have no dialogs and no segment.
 *
 * <p>A segment only converts NBT between the release's native tags and the library-neutral
 * {@link me.whereareiam.anvil.capability.protocol.api.model.nbt.NbtTag} model; the dialogs binding reads dialogs,
 * flattens their text, writes and parses SNBT, builds click payloads and decides which native connection generation
 * a dialog belongs to.</p>
 *
 * <p>Implementations are stateless and are called only with the player's current native session.</p>
 *
 * <pre>{@code
 * public final class McProtocolDialogsPackets implements DialogsPackets<Session> {
 *     public Class<Session> sessionType() { return Session.class; }
 *     public void command(Session session, String command) {
 *         session.send(new ServerboundChatCommandPacket(command));
 *     }
 *     public void customClick(Session session, String id, NbtCompound payload) {
 *         session.send(new ServerboundCustomClickActionPacket(Key.key(id), nativeCompound(payload)));
 *     }
 *     public Runnable listen(Session session, DialogsPacketListener listener) {
 *         SessionListener native = new SessionAdapter() {
 *             public void packetReceived(Session received, Packet packet) {
 *                 if (packet instanceof ClientboundShowDialogConfigurationPacket show) listener.shown(neutral(show.getDialog()), true);
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
	 * Sends a command the way a dialog button runs it: as an unsigned command.
	 *
	 * @param session the player's current native session
	 * @param commandWithoutSlash command and arguments without the leading slash
	 */
	void command(@NotNull S session, @NotNull String commandWithoutSlash);

	/**
	 * Sends a custom click with its payload.
	 *
	 * @param session the player's current native session
	 * @param id custom click ID, such as {@code example:submit}
	 * @param payload the payload compound, empty when the click carries none
	 */
	void customClick(@NotNull S session, @NotNull String id, @NotNull NbtCompound payload);

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
