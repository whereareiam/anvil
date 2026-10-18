package me.whereareiam.anvil.protocol.mcprotocol.client.v1_20_6;

import me.whereareiam.anvil.protocol.mcprotocol.client.ClientListener;
import me.whereareiam.anvil.protocol.mcprotocol.client.PacketTable;
import me.whereareiam.anvil.protocol.mcprotocol.client.model.ClientLogin;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.jetbrains.annotations.NotNull;

/**
 * Opens and connects the sessions of one MCProtocolLib release for {@link JavaClient}: the part of a client that
 * changes when a release changes how sessions are created, connected or answered, while the client itself does not.
 *
 * @param <S> the release's session type
 */
public interface ClientSessions<S extends Session> {
	/**
	 * Returns the class of the sessions this release opens.
	 *
	 * @return session class
	 */
	@NotNull Class<S> type();

	/**
	 * Creates an unconnected session for one login, whose listener answers what the client must answer on its own
	 * and decodes the session's packets through the session's own table.
	 *
	 * @param login profile, optional credentials, client settings and connection
	 * @param listener receiver of the session's state and packets
	 * @param packets the table the session decodes through
	 * @return new unconnected session
	 */
	@NotNull S open(
			@NotNull ClientLogin login,
			@NotNull ClientListener<? super S> listener,
			@NotNull PacketTable<Packet> packets
	);

	/**
	 * Starts connecting a session without waiting for its login.
	 *
	 * @param session session to connect
	 */
	void connect(@NotNull S session);
}
