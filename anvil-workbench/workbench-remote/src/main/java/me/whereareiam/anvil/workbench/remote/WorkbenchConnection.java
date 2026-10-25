package me.whereareiam.anvil.workbench.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import me.whereareiam.anvil.workbench.protocol.PinnedTrustManager;
import me.whereareiam.anvil.workbench.protocol.WireErrors;
import me.whereareiam.anvil.workbench.protocol.WireLines;
import me.whereareiam.anvil.workbench.protocol.WireMapper;
import me.whereareiam.anvil.workbench.protocol.WireProtocol;
import me.whereareiam.anvil.workbench.protocol.model.WorkbenchRequest;
import me.whereareiam.anvil.workbench.protocol.model.connection.Accepted;
import me.whereareiam.anvil.workbench.protocol.model.connection.ConnectionAnswer;
import me.whereareiam.anvil.workbench.protocol.model.connection.Control;
import me.whereareiam.anvil.workbench.protocol.model.connection.EngineConnection;
import me.whereareiam.anvil.workbench.protocol.model.connection.Pair;
import me.whereareiam.anvil.workbench.protocol.model.connection.Paired;
import me.whereareiam.anvil.workbench.protocol.model.connection.Refused;
import me.whereareiam.anvil.workbench.protocol.model.connection.Uploaded;
import me.whereareiam.anvil.workbench.protocol.model.message.Request;
import me.whereareiam.anvil.workbench.protocol.model.message.Response;
import me.whereareiam.anvil.workbench.protocol.model.message.ReverseConnection;
import me.whereareiam.anvil.workbench.protocol.model.message.ServerMessage;
import me.whereareiam.anvil.workbench.remote.pairing.Pairing;
import org.jetbrains.annotations.NotNull;

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
import java.util.function.Function;

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
	private final @NotNull Consumer<ReverseConnection> reverseConnections;
	private final Map<Long, CompletableFuture<Response>> pending = new ConcurrentHashMap<>();
	private final AtomicLong ids = new AtomicLong();
	private volatile boolean closed;

	private WorkbenchConnection(@NotNull Pairing pairing, @NotNull SSLContext tls, @NotNull Consumer<ReverseConnection> reverseConnections)
			throws IOException {
		this.pairing = pairing;
		this.tls = tls;
		this.reverseConnections = reverseConnections;
		this.control = stream(Control::new);
		Thread.ofPlatform().daemon().name("anvil-workbench-" + pairing.getName()).start(this::receive);
	}

	/**
	 * Connects to a paired workbench.
	 *
	 * @param pairing the engine's pairing with it
	 * @param reverseConnections receives the connections workbench processes make to exposed ports
	 * @return the open connection
	 * @throws WorkbenchException when the workbench cannot be reached or refuses the engine
	 */
	public static @NotNull WorkbenchConnection open(@NotNull Pairing pairing, @NotNull Consumer<ReverseConnection> reverseConnections) {
		try {
			return new WorkbenchConnection(pairing, tls(new PinnedTrustManager(pairing.getFingerprint())), reverseConnections);
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
		try (SSLSocket socket = socket(unpaired, tls(trust))) {
			WireLines.write(mapper, socket.getOutputStream(), new Pair(code, engine));
			return switch (answer(mapper, socket, "the pairing")) {
				case Paired paired -> unpaired.toBuilder().fingerprint(trust.seen()).token(paired.getToken()).build();
				case Refused refused -> throw new IOException(refused.getReason());
				case Accepted ignored -> throw new IOException("The workbench accepted a pairing without pairing");
				case Uploaded ignored -> throw new IOException("The workbench answered a pairing with an upload");
			};
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
	 * Sends a request and waits for its answer; a failure on the workbench is thrown as the engine would see it
	 * locally, an I/O failure wrapped in {@link UncheckedIOException}.
	 *
	 * @param request the request
	 * @param <R> type of its answer
	 * @return its answer
	 */
	public <R> @NotNull R call(@NotNull WorkbenchRequest<R> request) {
		try {
			return request(request);
		} catch (IOException failure) {
			throw new UncheckedIOException(failure);
		}
	}

	/**
	 * Sends a request whose failures include {@link IOException}, such as a file operation, and waits for its answer.
	 *
	 * @param request the request
	 * @param <R> type of its answer
	 * @return its answer
	 * @throws IOException when the request failed with one, or the connection failed
	 */
	public <R> @NotNull R request(@NotNull WorkbenchRequest<R> request) throws IOException {
		if (closed) throw new WorkbenchException("The connection to workbench '" + pairing.getName() + "' is closed");

		long id = ids.incrementAndGet();
		CompletableFuture<Response> answer = new CompletableFuture<>();
		pending.put(id, answer);
		try {
			WireLines.write(mapper, control.getOutputStream(), new Request(id, request));
			Response response = answer.get();
			if (response.getFailure() == null) return mapper.treeToValue(response.getResult(), request.responseType());

			Exception failure = WireErrors.restore(response.getFailure());
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
	 * Opens a further connection, such as the standard streams of a process.
	 *
	 * @param header creates the connection's header from the engine's token
	 * @return the accepted connection, positioned after the workbench's answer
	 * @throws IOException when the workbench cannot be reached or refuses the connection
	 */
	public @NotNull SSLSocket stream(@NotNull Function<String, EngineConnection> header) throws IOException {
		SSLSocket socket = socket(pairing, tls);
		try {
			WireLines.write(mapper, socket.getOutputStream(), header.apply(pairing.getToken()));
			switch (answer(mapper, socket, "a connection")) {
				case Accepted ignored -> {
				}
				case Refused refused -> throw new IOException(refused.getReason());
				case Paired ignored -> throw new IOException("Workbench '" + pairing.getName() + "' answered a connection with a pairing");
				case Uploaded ignored -> throw new IOException("Workbench '" + pairing.getName() + "' answered a connection with an upload");
			}
			return socket;
		} catch (IOException | RuntimeException failure) {
			socket.close();
			throw failure;
		}
	}

	/**
	 * Reads the answer a connection's header received.
	 *
	 * @param mapper mapper of the connection
	 * @param socket the connection
	 * @param purpose what the connection is for, for the failure
	 * @return the workbench's answer
	 * @throws IOException when the workbench closed the connection instead
	 */
	static @NotNull ConnectionAnswer answer(@NotNull ObjectMapper mapper, @NotNull Socket socket, @NotNull String purpose) throws IOException {
		ConnectionAnswer answer = WireLines.read(mapper, socket.getInputStream(), ConnectionAnswer.class);
		if (answer == null) throw new IOException("The workbench closed " + purpose);
		return answer;
	}

	private void receive() {
		Throwable cause = null;
		try {
			ServerMessage message;
			while ((message = WireLines.read(mapper, control.getInputStream(), ServerMessage.class)) != null) {
				switch (message) {
					case Response response -> {
						CompletableFuture<Response> answer = pending.get(response.getId());
						if (answer != null) answer.complete(response);
					}
					case ReverseConnection reverse -> reverseConnections.accept(reverse);
				}
			}
		} catch (IOException failure) {
			cause = failure;
		}

		closed = true;
		IOException lost = new IOException(cause == null ? "the workbench closed the connection" : cause.getMessage(), cause);
		pending.values().forEach(answer -> answer.completeExceptionally(lost));
	}

	@Override
	public void close() throws IOException {
		closed = true;
		control.close();
	}

	private static SSLContext tls(PinnedTrustManager trust) throws IOException {
		try {
			SSLContext context = SSLContext.getInstance("TLS");
			context.init(null, new TrustManager[]{trust}, null);
			return context;
		} catch (GeneralSecurityException failure) {
			throw new IOException("TLS is not available", failure);
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
