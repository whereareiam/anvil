package me.whereareiam.anvil.agent.client;

import lombok.Builder;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnectionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Owns credentials and the host connection for one generation of a process agent.
 * The connection is established explicitly after the process starts.
 */
public final class AgentSession implements AutoCloseable {
	private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(20);

	private final @NotNull ProcessAgentClient client;
	private final @NotNull AgentConnectionProvider connections;
	private final int connectPort;
	private final @NotNull String token;
	private final @NotNull Map<String, String> environment;

	private @Nullable AgentClient connection;
	private boolean closed;

	@Builder
	private AgentSession(
			@NotNull ProcessAgentClient client,
			@NotNull AgentConnectionProvider connections,
			int bindPort,
			@NotNull String bindAddress,
			int connectPort
	) {
		this.client = client;
		this.connections = connections;
		this.connectPort = connectPort;

		byte[] secret = new byte[32];
		new SecureRandom().nextBytes(secret);
		token = HexFormat.of().formatHex(secret);
		environment = Map.of(
				"ANVIL_AGENT_PORT", Integer.toString(bindPort),
				"ANVIL_AGENT_TOKEN", token,
				"ANVIL_AGENT_BIND", bindAddress
		);
	}

	/**
	 * Returns the immutable process environment needed to start this agent generation.
	 * The contained authentication token must not be logged.
	 *
	 * @return agent listener settings and authentication token
	 */
	public @NotNull Map<String, String> environment() {
		return environment;
	}

	/**
	 * Connects to the agent's host-side loopback endpoint and attaches it to the stable client.
	 * Repeated calls after a successful connection do not create another connection.
	 *
	 * @throws IllegalStateException when this session has closed or another generation is attached
	 */
	public synchronized void connect() {
		if (closed) throw new IllegalStateException("Cannot connect a closed agent session");
		if (connection != null) return;

		AgentClient connected = connections.connect(connectPort, token, CONNECTION_TIMEOUT);
		client.attach(connected);
		connection = connected;
	}

	/**
	 * Closes this generation's connection without disconnecting a replacement generation.
	 * Repeated calls have no effect, including when connection cleanup previously failed.
	 */
	@Override
	public synchronized void close() {
		if (closed) return;

		closed = true;
		if (connection != null) client.closeConnection(connection);
	}
}
