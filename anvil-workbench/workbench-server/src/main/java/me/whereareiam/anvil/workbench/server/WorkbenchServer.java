package me.whereareiam.anvil.workbench.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.engine.config.EngineDefaults;
import me.whereareiam.anvil.environment.execution.api.ExecutionProvider;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.workbench.local.LocalWorkbench;
import me.whereareiam.anvil.workbench.protocol.WireLines;
import me.whereareiam.anvil.workbench.protocol.WireMapper;
import me.whereareiam.anvil.workbench.protocol.WireProtocol;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A workbench on its own machine, serving the engines paired with it over TLS. Every connection begins with a
 * header line naming its kind and, except while pairing, the engine's token; the server then serves a control
 * connection, relays the standard streams of a process, receives an upload, or carries a tunnel.
 */
public final class WorkbenchServer implements AutoCloseable {
	private final @NotNull ServerSettings settings;
	private final @NotNull ObjectMapper mapper = WireMapper.create();
	private final @NotNull ServerIdentity identity;
	private final @NotNull LocalWorkbench workbench;
	private final @NotNull Handles handles = new Handles();
	private final @NotNull UploadStore uploads;
	private final @NotNull Map<String, Socket> waitingReverse = new ConcurrentHashMap<>();
	private final @NotNull SSLServerSocket listener;

	/**
	 * Opens the workbench's identity and machine side and starts listening; connections are served once
	 * {@link #serve()} runs.
	 *
	 * @param settings operator's settings
	 * @throws IOException when the identity cannot be opened or the port cannot be bound
	 */
	public WorkbenchServer(@NotNull ServerSettings settings) throws IOException {
		this.settings = settings;
		Path data = settings.getDataDirectory();
		identity = ServerIdentity.open(data, mapper, settings.getName());
		uploads = new UploadStore(data.resolve("store"));
		workbench = new LocalWorkbench(EngineDefaults.resolve(EngineOptions.builder()
				.cacheDirectory(data.resolve("cache"))
				.workDirectory(data.resolve("work"))
				.accountsDirectory(data.resolve("accounts"))
				.build()), executions(settings));
		try {
			listener = (SSLServerSocket) identity.tls().getServerSocketFactory()
					.createServerSocket(settings.getPort(), 50, InetAddress.getByName(settings.getBindAddress()));
		} catch (IOException | RuntimeException failure) {
			workbench.close();
			throw failure;
		}
	}

	/**
	 * Returns the port the workbench listens on, which differs from the configured one when that was 0.
	 *
	 * @return listening port
	 */
	public int port() {
		return listener.getLocalPort();
	}

	/**
	 * Returns the fingerprint engines pin when pairing.
	 *
	 * @return certificate fingerprint
	 */
	public @NotNull String fingerprint() {
		return identity.fingerprint();
	}

	/**
	 * Creates a one-time pairing code.
	 *
	 * @return the code
	 * @throws IOException when it cannot be written
	 */
	public @NotNull String newPairingCode() throws IOException {
		return identity.newPairingCode();
	}

	/**
	 * Returns whether any engine is paired yet.
	 *
	 * @return true when an engine holds a token
	 * @throws IOException when the identity cannot be read
	 */
	public boolean paired() throws IOException {
		return identity.paired();
	}

	/**
	 * Accepts and serves connections until the workbench closes.
	 */
	public void serve() {
		while (!listener.isClosed()) {
			try {
				SSLSocket socket = (SSLSocket) listener.accept();
				Thread.ofVirtual().name("anvil-connection").start(() -> route(socket));
			} catch (IOException closed) {
				return;
			}
		}
	}

	/**
	 * Serves connections on a background thread.
	 */
	public void start() {
		Thread.ofPlatform().name("anvil-workbench").start(this::serve);
	}

	private void route(SSLSocket socket) {
		try {
			socket.setTcpNoDelay(true);
			InputStream input = socket.getInputStream();
			JsonNode header = WireLines.read(mapper, input);
			if (header == null) {
				socket.close();
				return;
			}

			String kind = header.path(WireProtocol.KIND).asText();
			if (kind.equals(WireProtocol.PAIR)) {
				pair(socket, header);
				return;
			}

			String token = header.path(WireProtocol.TOKEN).asText();
			String engine = identity.engine(token);
			if (engine == null) {
				refuse(socket, "This workbench is not paired with the engine; pair it again");
				return;
			}

			switch (kind) {
				case WireProtocol.CONTROL -> {
					accept(socket);
					new EngineSession(socket, mapper, token, engine, workbench, settings, handles, uploads, waitingReverse).serve();
				}
				case WireProtocol.STDIO -> stdio(socket, token, header.path("handle").asText());
				case WireProtocol.STDERR -> stderr(socket, token, header.path("handle").asText());
				case WireProtocol.UPLOAD -> upload(socket, header);
				case WireProtocol.TUNNEL -> tunnel(socket, header);
				case WireProtocol.REVERSE -> reverse(socket, header.path("id").asText());
				default -> refuse(socket, "Unknown connection kind '" + kind + "'");
			}
		} catch (IOException | RuntimeException failure) {
			try {
				socket.close();
			} catch (IOException ignored) {
				// The connection is gone either way.
			}
		}
	}

	private void pair(Socket socket, JsonNode header) throws IOException {
		try (socket) {
			String token = identity.pair(header.path("code").asText(), header.path("engine").asText("engine"));
			if (token == null) {
				WireLines.write(mapper, socket.getOutputStream(), mapper.createObjectNode()
						.put("error", "The pairing code is wrong or expired; create a new one with 'pair' on the workbench"));
				return;
			}

			System.out.println("Paired with engine '" + header.path("engine").asText("engine") + "'");
			WireLines.write(mapper, socket.getOutputStream(), mapper.createObjectNode()
					.put("token", token).put("name", settings.getName()));
		}
	}

	private void stdio(Socket socket, String token, String handle) throws IOException {
		Object value = lookup(token, handle);
		accept(socket);
		InputStream fromEngine = socket.getInputStream();
		OutputStream toEngine = socket.getOutputStream();
		if (value instanceof Process process) {
			StreamPipes.copy("anvil-stdout", process.getInputStream(), toEngine, socket);
			StreamPipes.copy("anvil-stdin", fromEngine, process.getOutputStream(), process.getOutputStream());
			return;
		}

		ProcessExecution execution = (ProcessExecution) value;
		StreamPipes.copy("anvil-output", execution.output(), toEngine, socket);
		StreamPipes.copy("anvil-input", fromEngine, execution.input(), execution.input());
	}

	private void stderr(Socket socket, String token, String handle) throws IOException {
		Process process = handles.get(token, handle, Process.class);
		accept(socket);
		StreamPipes.copy("anvil-stderr", process.getErrorStream(), socket.getOutputStream(), socket);
	}

	private Object lookup(String token, String handle) {
		try {
			return handles.get(token, handle, Process.class);
		} catch (IllegalArgumentException notAProcess) {
			return handles.get(token, handle, ProcessExecution.class);
		}
	}

	private void upload(Socket socket, JsonNode header) throws IOException {
		try (socket) {
			accept(socket);
			Path stored = uploads.receive(header.path("sha256").asText(), header.path("name").asText(),
					header.path("directory").asBoolean(), new BoundedInput(socket.getInputStream(), header.path("size").asLong()));
			WireLines.write(mapper, socket.getOutputStream(), mapper.createObjectNode().put("path", stored.toString()));
		}
	}

	/**
	 * Connects to an address on the workbench's own loopback, such as a process's game or agent port, and relays it.
	 */
	private void tunnel(Socket socket, JsonNode header) throws IOException {
		int port = header.path("port").asInt();
		Socket target = new Socket();
		try {
			target.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 5000);
		} catch (IOException unreachable) {
			target.close();
			refuse(socket, "Nothing listens on port " + port + " of the workbench");
			return;
		}

		accept(socket);
		relay(socket, target);
	}

	private void reverse(Socket socket, String id) throws IOException {
		Socket waiting = waitingReverse.remove(id);
		if (waiting == null) {
			refuse(socket, "No connection waits under " + id);
			return;
		}

		accept(socket);
		relay(socket, waiting);
	}

	private static void relay(Socket engine, Socket local) throws IOException {
		StreamPipes.copy("anvil-relay", engine.getInputStream(), local.getOutputStream(), local);
		StreamPipes.copy("anvil-relay", local.getInputStream(), engine.getOutputStream(), engine);
	}

	private void accept(Socket socket) throws IOException {
		WireLines.write(mapper, socket.getOutputStream(), mapper.createObjectNode().put("accepted", true));
	}

	private void refuse(Socket socket, String reason) throws IOException {
		try (socket) {
			WireLines.write(mapper, socket.getOutputStream(), mapper.createObjectNode().put("error", reason));
		}
	}

	/**
	 * Installs the execution providers the policy allows. When it allows Docker but not processes on the machine
	 * itself, Docker also runs what a scenario would run locally, so every scenario runs isolated.
	 */
	private static Map<String, ExecutionProvider> executions(ServerSettings settings) {
		Map<String, ExecutionProvider> installed = new LinkedHashMap<>();
		ServiceLoader.load(ExecutionProvider.class, WorkbenchServer.class.getClassLoader())
				.forEach(provider -> installed.put(provider.id(), provider));

		Map<String, ExecutionProvider> allowed = new LinkedHashMap<>();
		for (String id : settings.getExecutions()) {
			ExecutionProvider provider = installed.get(id);
			if (provider == null) throw new IllegalArgumentException("The policy allows execution '" + id + "', which is not installed");
			allowed.put(id, provider);
		}
		if (!allowed.containsKey(ServerSettings.LOCAL) && allowed.containsKey(ServerSettings.DOCKER))
			allowed.put(ServerSettings.LOCAL, allowed.get(ServerSettings.DOCKER));
		return allowed;
	}

	@Override
	public void close() throws IOException {
		try (workbench) {
			listener.close();
		}
	}

	/**
	 * Ends an upload after the size its header announced, as the engine keeps the connection open for the answer.
	 */
	private static final class BoundedInput extends InputStream {
		private final InputStream input;
		private long remaining;

		private BoundedInput(InputStream input, long size) {
			this.input = input;
			this.remaining = size;
		}

		@Override
		public int read() throws IOException {
			if (remaining <= 0) return -1;
			int value = input.read();
			if (value >= 0) remaining--;
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			if (remaining <= 0) return -1;
			int read = input.read(buffer, offset, (int) Math.min(length, remaining));
			if (read > 0) remaining -= read;
			return read;
		}
	}
}
