package me.whereareiam.anvil.protocol.cloudburst.client;

import me.whereareiam.anvil.protocol.cloudburst.client.model.ClientLogin;
import org.jetbrains.annotations.NotNull;

/**
 * Client side of one CloudburstMC Protocol release, implemented by exactly one client segment on a worker's class
 * path.
 *
 * <p>The worker shell never links against CloudburstMC Protocol: it opens, connects and disconnects native sessions
 * only through this port. One release speaks many Bedrock protocols, so a segment selects the codec of each session
 * by its connection's protocol number. A segment owns every packet the client must answer on its own, from the
 * network settings request through the login, encryption handshake, resource pack responses and chunk radius
 * request to announcing that the player is initialized, and reports the resulting state through
 * {@link ClientListener}. Its sessions are {@link CloudburstSession}s, which the worker hands to capability bindings as
 * the player's native session; the shell passes them back to the segment that created them and never reads them.</p>
 *
 * <p>Segments register their implementation in
 * {@code META-INF/services/me.whereareiam.anvil.protocol.cloudburst.client.CloudburstClient}:</p>
 *
 * <pre>{@code
 * public final class CloudburstClientAdapter implements CloudburstClient<ConnectionSession> {
 *     public int protocolNumber() { return NEWEST.getProtocolVersion(); }
 *     public Class<ConnectionSession> sessionType() { return ConnectionSession.class; }
 *     public ConnectionSession open(ClientLogin login, ClientListener<? super ConnectionSession> listener) { ... }
 *     ...
 * }
 * }</pre>
 *
 * @param <S> native session type of the release
 */
public interface CloudburstClient<S extends CloudburstSession<?, ?, ?>> {
	/**
	 * Identifier of the CloudburstMC Protocol library: scenario and player declarations select it, stored accounts
	 * and segment descriptors name it, and its release cache lies below it.
	 */
	String LIBRARY_ID = "cloudburst";

	/**
	 * Returns the protocol number of the newest codec the loaded release speaks, which is the protocol of the
	 * release key.
	 *
	 * @return newest native protocol number
	 */
	int protocolNumber();

	/**
	 * Returns the class of the sessions this client opens; capability bindings receive sessions of this type, which
	 * implements {@link CloudburstSession}.
	 *
	 * @return native session class
	 */
	@NotNull Class<S> sessionType();

	/**
	 * Creates an unconnected session for one login. The listener is installed before the session connects and
	 * receives the session it was created for, so that callers can ignore callbacks of replaced sessions.
	 *
	 * <p>The session binds its datagram socket to the connection's source address when one is set; a segment never
	 * ignores it.</p>
	 *
	 * @param login profile, identity and connection
	 * @param listener receiver of login, view and disconnect callbacks for this session
	 * @return new unconnected session
	 * @throws IllegalArgumentException when the release has no codec for the connection's protocol
	 */
	@NotNull S open(@NotNull ClientLogin login, @NotNull ClientListener<? super S> listener);

	/**
	 * Starts connecting a session returned by {@link #open(ClientLogin, ClientListener)} without blocking until
	 * login completes.
	 *
	 * @param session session to connect
	 */
	void connect(@NotNull S session);

	/**
	 * Tests whether a session currently has an open connection.
	 *
	 * @param session session to test
	 * @return whether the session is connected
	 */
	boolean connected(@NotNull S session);

	/**
	 * Closes a session's connection; the listener later receives the disconnect.
	 *
	 * @param session session to disconnect
	 * @param reason plain-text reason of the local session
	 */
	void disconnect(@NotNull S session, @NotNull String reason);
}
