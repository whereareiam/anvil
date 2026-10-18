package me.whereareiam.anvil.protocol.java.worker;

import me.whereareiam.anvil.protocol.api.exception.ProtocolUnavailableException;
import me.whereareiam.anvil.protocol.java.api.packet.JavaPacket;
import me.whereareiam.anvil.protocol.java.api.packet.ServerboundPacket;
import org.jetbrains.annotations.NotNull;

/**
 * What a protocol library supplies for a player's Java Edition protocol: sending a packet on the player's current
 * connection in the release's form, and which packets the release carries. The library hands every packet the player
 * receives to {@link JavaPacketDispatcher#deliver}.
 *
 * <pre>{@code
 * JavaConnection connection = new JavaConnection() {
 *     public void send(ServerboundPacket packet) { client.send(player.nativeSession(), packet); }
 *     public boolean supports(Class<? extends JavaPacket> type) { return client.supports(type); }
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
}
