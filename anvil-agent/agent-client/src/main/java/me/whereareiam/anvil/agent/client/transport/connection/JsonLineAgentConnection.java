package me.whereareiam.anvil.agent.client.transport.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnection;
import me.whereareiam.anvil.agent.api.exception.AgentException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Synchronous authenticated JSON-lines connection to one platform agent.
 */
final class JsonLineAgentConnection implements AgentConnection {
	private final String token;
	private final BufferedReader reader;
	private final BufferedWriter writer;

	private final ObjectMapper mapper = new ObjectMapper();
	private final AtomicLong ids = new AtomicLong();
	private final Socket socket = new Socket();

	JsonLineAgentConnection(int port, @NotNull String token) {
		this.token = token;
		try {
			socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 1000);
			socket.setSoTimeout(5000);
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
		} catch (IOException exception) {
			close();
			throw new AgentException("Could not connect to platform agent on loopback:" + port, exception);
		}
	}

	@Override
	public synchronized <T> @Nullable T request(
			@NotNull String operation,
			@Nullable Object arguments,
			@NotNull Class<T> responseType
	) {
		ObjectNode request = mapper.createObjectNode();
		long id = ids.incrementAndGet();
		request.put("id", id);
		request.put("token", token);
		request.put("operation", operation);
		request.set("arguments", mapper.valueToTree(arguments));

		try {
			writer.write(mapper.writeValueAsString(request));
			writer.newLine();
			writer.flush();

			String line = reader.readLine();
			if (line == null) throw new AgentException("Platform agent closed the connection");

			JsonNode response = mapper.readTree(line);
			if (response == null || !response.path("id").isIntegralNumber() || response.path("id").asLong() != id) {
				close();
				throw new AgentException("Mismatched response for agent operation '" + operation + "'");
			}

			if (!response.path("success").asBoolean())
				throw new AgentException("Agent operation '" + operation + "' failed: "
						+ response.path("error").asText());

			JsonNode result = response.path("result");
			if (result.isNull() || result.isMissingNode()) return null;

			return mapper.treeToValue(result, responseType);
		} catch (IOException exception) {
			close();
			throw new AgentException("Agent operation '" + operation + "' failed", exception);
		}
	}

	@Override
	public void close() {
		try {
			socket.close();
		} catch (IOException ignored) {
			// Closing an already-closed agent is harmless.
		}
	}
}
