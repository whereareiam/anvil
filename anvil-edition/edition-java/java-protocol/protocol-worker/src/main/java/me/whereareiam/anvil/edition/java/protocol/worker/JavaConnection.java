package me.whereareiam.anvil.edition.java.protocol.worker;

import me.whereareiam.anvil.edition.java.protocol.packet.JavaPacket;
import me.whereareiam.anvil.edition.java.protocol.packet.ServerboundPacket;
import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import org.jetbrains.annotations.NotNull;

/**
 * What a protocol library supplies for a player's Java Edition protocol: sending a packet on the player's current
 * connection in the release's form, which packets the release carries, and whether it hands over the frames it
 * receives. The library hands every packet the player receives to {@link JavaPacketDispatcher}: as its frame while the
 * dispatcher {@linkplain JavaPacketDispatcher#wantsFrames() wants frames}, else as its value alone.
 *
 * <pre>{@code
 * JavaConnection connection = new JavaConnection() {
 *     public void send(ServerboundPacket packet) { client.send(player.nativeSession(), packet); }
 *     public boolean supports(Class<? extends JavaPacket> type) { return client.supports(type); }
 *     public boolean handsOverFrames() { return true; }
 * };
 * }</pre>
 */
public interface JavaConnection {
	/**
	 * Sends a packet on the player's current connection.
	 *
	 * @param packet packet to send
	 * @throws ProtocolUnavailableException when the release cannot carry the packet
	 * @throws IllegalStateException when the player is not connected
	 */
	void send(@NotNull ServerboundPacket packet);

	/**
	 * Tells whether the player's release carries a packet, in either direction.
	 *
	 * @param type packet class
	 * @return whether the release carries it
	 */
	boolean supports(@NotNull Class<? extends JavaPacket> type);

	/**
	 * Tells whether the library hands every packet the player receives to the dispatcher as its frame, its bytes as
	 * they arrived, while the dispatcher wants frames. A library that never sees the wire, such as one running a
	 * player in process, hands over values alone, and the protocol refuses frame listeners.
	 *
	 * @return whether the library hands over frames
	 */
	boolean handsOverFrames();
}
