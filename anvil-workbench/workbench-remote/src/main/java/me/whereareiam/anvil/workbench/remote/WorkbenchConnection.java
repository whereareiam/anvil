package me.whereareiam.anvil.workbench.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import me.whereareiam.anvil.workbench.protocol.PinnedTrustManager;
import me.whereareiam.anvil.workbench.protocol.WireErrors;
import me.whereareiam.anvil.workbench.protocol.WireLines;
import me.whereareiam.anvil.workbench.protocol.WireMapper;
import me.whereareiam.anvil.workbench.protocol.WireProtocol;
import me.whereareiam.anvil.workbench.remote.pairing.Pairing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * An engine's connection to one paired workbench: a control connection for requests, and further TLS connections
 * opened per stream, such as a process's standard streams or a tunnel to a port. Every connection trusts only the
 * certificate pinned when pairing and presents the engine's token.
 */
public final class WorkbenchConnection implements AutoCloseable {
	private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

	private final @NotNull Pairing pairing;
	private final @NotNull ObjectMapper mapper = WireMapper.create();
	private final @NotNull SSLContext tls;
	private final @NotNull SSLSocket control;
	private final @NotNull Consumer<JsonNode> events;
	private final Map<Long, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
	private final AtomicLong ids = new AtomicLong();
	private volatile boolean closed;

	private WorkbenchConnection(@NotNull Pairing pairing, @NotNull SSLContext tls, @NotNull Consumer<JsonNode> events) throws IOException {
		this.pairing = pairing;
		this.tls = tls;
		this.events = events;
		this.control = stream(WireProtocol.CONTROL, mapper.createObjectNode());
		Thread.ofPlatform().daemon().name("anvil-workbench-" + pairing.getName()).start(this::receive);
	}

	/**
	 * Connects to a paired workbench.
	 *
	 * @param pairing the engine's pairing with it
	 * @param events receives the events the workbench sends unasked
	 * @return the open connection
	 * @throws WorkbenchException when the workbench cannot be reached or refuses the engine
	 */
	public static @NotNull WorkbenchConnection open(@NotNull Pairing pairing, @NotNull Consumer<JsonNode> events) {
		try {
			return new WorkbenchConnection(pairing, tls(pairing.getFingerprint(), new PinnedTrustManager(pairing.getFingerprint())), events);
		} catch (IOException failure) {
			throw new WorkbenchException("Could not reach workbench '" + pairing.getName() + "' at " + pairing.getAddress()
					+ ": " + failure.getMessage(), failure);
		}
	}

	/**
	 * Pairs the engine with a workbench through a one-time code the workbench printed, trusting the certificate the
	 * workbench presents now.
	 *
	 * @param name name the engine will know the workbench by
	 * @param address host and port of the workbench
	 * @param code pairing code
	 * @param engine name the workbench will know the engine by
	 * @return the new pairing
	 * @throws IOException when the workbench cannot be reached or refuses the code
	 */
	public static @NotNull Pairing pair(@NotNull String name, @NotNull String address, @NotNull String code, @NotNull String engine) throws IOException {
		PinnedTrustManager trust = new PinnedTrustManager(null);
		Pairing unpaired = Pairing.builder().name(name).address(address).fingerprint("").token("").build();
		ObjectMapper mapper = WireMapper.create();
		try (SSLSocket socket = socket(unpaired, tls(null, trust))) {
			WireLines.write(mapper, socket.getOutputStream(), mapper.createObjectNode()
					.put(WireProtocol.KIND, WireProtocol.PAIR).put("code", code).put("engine", engine));
			JsonNode answer = WireLines.read(mapper, socket.getInputStream());
			if (answer == null) throw new IOException("The workbench closed the connection while pairing");
			if (answer.has("error")) throw new IOException(answer.path("error").asText());

			return unpaired.toBuilder().fingerprint(trust.seen()).token(answer.path("token").asText()).build();
		}
	}

	/**
	 * Returns the mapper the connection speaks with.
	 *
	 * @return JSON mapper
	 */
	public @NotNull ObjectMapper mapper() {
		return mapper;
	}

	/**
	 * Returns the pairing the connection uses.
	 *
	 * @return pairing
	 */
	public @NotNull Pairing pairing() {
		return pairing;
	}

	/**
	 * Sends a request and waits for its result; a failure on the workbench is thrown as the engine would see it
	 * locally, an I/O failure wrapped in {@link UncheckedIOException}.
	 *
	 * @param operation operation name
	 * @param arguments its arguments
	 * @return its result
	 */
	public @NotNull JsonNode call(@NotNull String operation, @NotNull ObjectNode arguments) {
		try {
			return request(operation, arguments);
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	/**
	 * Sends a request whose failures include {@link IOException}, such as a file operation, and waits for it.
	 *
	 * @param operation operation name
	 * @param arguments its arguments
	 * @return its result
	 * @throws IOException when the operation failed with one, or the connection failed
	 */
	public @NotNull JsonNode request(@NotNull String operation, @NotNull ObjectNode arguments) throws IOException {
		if (closed) throw new WorkbenchException("The connection to workbench '" + pairing.getName() + "' is closed");

		long id = ids.incrementAndGet();
		CompletableFuture<JsonNode> response = new CompletableFuture<>();
		pending.put(id, response);
		try {
			WireLines.write(mapper, control.getOutputStream(), mapper.createObjectNode().put("id", id).put("op", operation).set("args", arguments));
			JsonNode answer = response.get();
			if (!answer.has("error")) return answer.path("result");

			Exception failure = WireErrors.restore(answer.path("error"));
			if (failure instanceof IOException io) throw io;
			throw (RuntimeException) failure;
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new WorkbenchException("Interrupted while waiting for workbench '" + pairing.getName() + "'", interrupted);
		} catch (ExecutionException lost) {
			throw new WorkbenchException("Lost workbench '" + pairing.getName() + "': " + lost.getCause().getMessage(), lost.getCause());
		} finally {
			pending.remove(id);
		}
	}

	/**
	 * Opens a further connection of a kind, such as the standard streams of a process.
	 *
	 * @param kind connection kind
	 * @param header members the kind needs besides the token
	 * @return the accepted connection, positioned after the workbench's answer
	 * @throws IOException when the workbench cannot be reached or refuses the connection
	 */
	public @NotNull SSLSocket stream(@NotNull String kind, @NotNull ObjectNode header) throws IOException {
		SSLSocket socket = socket(pairing, tls);
		try {
			WireLines.write(mapper, socket.getOutputStream(), header.put(WireProtocol.KIND, kind).put(WireProtocol.TOKEN, pairing.getToken()));
			JsonNode answer = WireLines.read(mapper, socket.getInputStream());
			if (answer == null) throw new IOException("Workbench '" + pairing.getName() + "' closed the connection");
			if (answer.has("error")) throw new IOException(answer.path("error").asText());
			return socket;
		} catch (IOException | RuntimeException failure) {
			socket.close();
			throw failure;
		}
	}

	private void receive() {
		Throwable cause = null;
		try {
			JsonNode message;
			while ((message = WireLines.read(mapper, control.getInputStream())) != null) {
				if (message.has("event")) {
					events.accept(message);
					continue;
				}
				CompletableFuture<JsonNode> response = pending.get(message.path("id").asLong());
				if (response != null) response.complete(message);
			}
		} catch (IOException failure) {
			cause = failure;
		}

		closed = true;
		IOException lost = new IOException(cause == null ? "the workbench closed the connection" : cause.getMessage(), cause);
		pending.values().forEach(response -> response.completeExceptionally(lost));
	}

	@Override
	public void close() throws IOException {
		closed = true;
		control.close();
	}

	private static SSLContext tls(@Nullable String pinned, PinnedTrustManager trust) throws IOException {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[]{trust}, null);
			return context;
		} catch (GeneralSecurityException failure) {
			throw new IOException("TLS is not available" + (pinned == null ? "" : " for " + pinned), failure);
		}
	}

	private static SSLSocket socket(Pairing pairing, SSLContext tls) throws IOException {
		Socket plain = new Socket();
		plain.connect(new InetSocketAddress(InetAddress.getByName(pairing.host()), pairing.port(WireProtocol.DEFAULT_PORT)), CONNECT_TIMEOUT_MILLIS);
		plain.setTcpNoDelay(true);
		SSLSocket socket = (SSLSocket) tls.getSocketFactory().createSocket(plain, pairing.host(), plain.getPort(), true);
		socket.startHandshake();
		return socket;
	}
}
