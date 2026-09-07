package me.whereareiam.anvil.agent.common.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.agent.api.platform.PlatformAgent;
import me.whereareiam.anvil.agent.api.transport.AgentServer;
import me.whereareiam.anvil.agent.api.operation.AgentOperationProvider;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

/**
 * Authenticated loopback-only JSON-lines server embedded into managed platforms.
 */
public final class PlatformAgentServer implements AgentServer {
	private final ObjectMapper mapper = new ObjectMapper();

	private final String token;
	private final PlatformAgentRequestDispatcher dispatcher;
	private final Consumer<String> logger;
	private final ServerSocket server;

	private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
	private final Set<Socket> connections = ConcurrentHashMap.newKeySet();

	/**
	 * Starts the authenticated agent from Anvil-owned environment variables.
	 *
	 * @param platformAgent typed platform behavior
	 * @param logger platform logger callback
	 */
	public PlatformAgentServer(@NotNull PlatformAgent platformAgent, @NotNull Consumer<String> logger) {
		this(readPort(), readToken(), new PlatformAgentRequestDispatcher(platformAgent), logger);
	}

	PlatformAgentServer(
			PlatformAgent platformAgent,
			Collection<AgentOperationProvider> providers,
			Consumer<String> logger
	) {
		this(readPort(), readToken(), new PlatformAgentRequestDispatcher(platformAgent, providers), logger);
	}

	/**
	 * Starts an authenticated agent with explicit connection settings and typed platform behavior.
	 *
	 * @param port loopback port
	 * @param token bearer token
	 * @param platformAgent typed platform behavior
	 * @param logger platform logger callback
	 */
	public PlatformAgentServer(
			int port,
			@NotNull String token,
			@NotNull PlatformAgent platformAgent,
			@NotNull Consumer<String> logger
	) {
		this(port, token, new PlatformAgentRequestDispatcher(platformAgent), logger);
	}

	PlatformAgentServer(
			int port,
			@NotNull String token,
			@NotNull PlatformAgentRequestDispatcher dispatcher,
			@NotNull Consumer<String> logger
	) {
		this.token = token;
		this.dispatcher = dispatcher;
		this.logger = logger;

		try {
			server = new ServerSocket(port, 20, InetAddress.getByName(System.getenv().getOrDefault("ANVIL_AGENT_BIND", "127.0.0.1")));
		} catch (IOException e) {
			throw new IllegalStateException("Could not bind Anvil agent on loopback:" + port, e);
		}

		Thread acceptor = new Thread(this::accept, "anvil-platform-agent-acceptor");
		acceptor.setDaemon(true);
		acceptor.start();
		logger.accept("Anvil agent listening on loopback:" + port);
	}

	private static int readPort() {
		String value = System.getenv("ANVIL_AGENT_PORT");
		if (value == null)
			throw new IllegalStateException("Anvil agent environment is missing ANVIL_AGENT_PORT");
		return Integer.parseInt(value);
	}

	private static String readToken() {
		String value = System.getenv("ANVIL_AGENT_TOKEN");
		if (value == null)
			throw new IllegalStateException("Anvil agent environment is missing ANVIL_AGENT_TOKEN");
		return value;
	}

	private void accept() {
		while (!server.isClosed()) {
			try {
				serve(server.accept());
			} catch (IOException e) {
				if (!server.isClosed())
					logger.accept("Anvil agent accept failed: " + e.getMessage());
			}
		}
	}

	private void serve(Socket socket) {
		connections.add(socket);
		if (server.isClosed()) {
			closeConnection(socket);
			return;
		}

		try {
			clients.submit(() -> handle(socket));
		} catch (RejectedExecutionException exception) {
			closeConnection(socket);
		}
	}

	private void handle(Socket socket) {
		try (socket;
			 BufferedReader reader = new BufferedReader(
					 new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
			 BufferedWriter writer = new BufferedWriter(
					 new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
			String line;

			while ((line = reader.readLine()) != null) {
				writer.write(mapper.writeValueAsString(respond(line)));
				writer.newLine();
				writer.flush();
			}
		} catch (IOException e) {
			if (!server.isClosed()) logger.accept("Anvil agent client failed: " + e.getMessage());
		} finally {
			connections.remove(socket);
		}
	}

	private ObjectNode respond(String line) {
		ObjectNode response = mapper.createObjectNode();

		try {
			JsonNode request = mapper.readTree(line);
			response.put("id", request.path("id").asLong());
			if (!authorized(request.path("token").asText())) return response.put("success", false).put("error", "Unauthorized");

			JsonNode result = dispatcher.handle(request.path("operation").asText(), request.path("arguments"));
			response.put("success", true);
			response.set("result", result);

			return response;
		} catch (Exception exception) {
			return response.put("success", false)
					.put("error", exception.getClass().getSimpleName() + ": " + exception.getMessage());
		}
	}

	private void closeConnection(Socket socket) {
		try {
			socket.close();
		} catch (IOException ignored) {
			// Continue closing the other accepted connections.
		} finally {
			connections.remove(socket);
		}
	}

	private boolean authorized(String candidate) {
		return MessageDigest.isEqual(
				token.getBytes(StandardCharsets.UTF_8),
				candidate.getBytes(StandardCharsets.UTF_8)
		);
	}

	/**
	 * Stops accepting agent requests and closes all client tasks.
	 */
	@Override
	public void close() {
		try {
			server.close();
		} catch (IOException ignored) {
			// Closing an already-closed agent is harmless.
		}

		connections.forEach(this::closeConnection);
		clients.shutdownNow();
	}
}
