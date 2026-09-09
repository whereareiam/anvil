package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.mcprotocol.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.mcprotocol.model.ProtocolDefinition;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerEvent;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerReady;
import me.whereareiam.anvil.protocol.mcprotocol.model.worker.WorkerResponse;
import me.whereareiam.anvil.protocol.mcprotocol.type.WorkerControlOperation;
import me.whereareiam.anvil.protocol.mcprotocol.worker.child.McProtocolWorkerMain;
import me.whereareiam.anvil.protocol.mcprotocol.worker.transport.WorkerMessageCodec;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Owns one isolated worker process shared by players using the same protocol version.
 */
public final class ProtocolWorkerProcess implements AutoCloseable {
	private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
	private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

	private final ProtocolDefinition definition;
	private final WorkerMessageCodec codec = new WorkerMessageCodec();
	private final Map<String, RemoteProtocolPlayer> players = new ConcurrentHashMap<>();
	private final CompletableFuture<Void> ready = new CompletableFuture<>();
	private final WorkerDiagnostics diagnostics = new WorkerDiagnostics();

	private final Process process;
	private final WorkerRpcClient requests;
	private volatile Set<String> workerCapabilities = Set.of();

	/**
	 * Launches and validates the exact catalog worker before exposing its request channel.
	 *
	 * @param definition  verified runtime selection
	 * @param protocolJar verified exact protocol runtime supplied by the backend
	 */
	public ProtocolWorkerProcess(@NotNull ProtocolDefinition definition, @NotNull Path protocolJar) {
		this.definition = definition;
		process = launch(protocolJar);
		requests = new WorkerRpcClient(process.getOutputStream());

		try {
			startReaders();
			ready.get(STARTUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw startupFailure(exception);
		} catch (Exception exception) {
			process.destroyForcibly();
			throw startupFailure(exception);
		}
	}

	/**
	 * Creates an initially disconnected player owned by this worker.
	 *
	 * @param request        resolved player options
	 * @param authentication optional online session delivered only over private stdin
	 * @return host-side handle for the child worker's native player
	 */
	public @NotNull ProtocolPlayer create(@NotNull PlayerRequest request, @Nullable AuthenticationSession authentication) {
		return new RemoteProtocolPlayer(this, request, authentication);
	}

	@NotNull Set<String> workerCapabilities() {
		return workerCapabilities;
	}

	void register(@NotNull String id, @NotNull RemoteProtocolPlayer player) {
		players.put(id, player);
	}

	void unregister(@NotNull String id) {
		players.remove(id);
	}

	@NotNull JsonNode request(@NotNull String operation, @Nullable String playerId, @NotNull Consumer<ObjectNode> arguments) {
		ObjectNode payload = JsonNodeFactory.instance.objectNode();
		arguments.accept(payload);
		return request(operation, playerId, payload, REQUEST_TIMEOUT);
	}

	@NotNull JsonNode control(@NotNull WorkerControlOperation operation, @Nullable String playerId, @NotNull Object arguments) {
		return request(operation.getWireName(), playerId, codec.payload(arguments), REQUEST_TIMEOUT);
	}

	private JsonNode request(String operation, @Nullable String playerId, JsonNode arguments, Duration timeout) {
		try {
			return requests.request(operation, playerId, arguments, timeout);
		} catch (IllegalStateException exception) {
			throw new IllegalStateException(exception.getMessage() + diagnostics.tail(), exception);
		}
	}

	void recordDiagnostic(@NotNull String message) {
		diagnostics.remember(message);
	}

	@NotNull String diagnosticTail() {
		return diagnostics.tail();
	}

	private Process launch(Path protocolJar) {
		String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
		String classpath = new WorkerClasspathResolver().resolve(protocolJar, Thread.currentThread().getContextClassLoader());
		List<String> command = List.of(
				Path.of(System.getProperty("java.home"), "bin", executable).toString(),
				"-Dorg.slf4j.simpleLogger.logFile=System.err",
				"-Dorg.slf4j.simpleLogger.defaultLogLevel=warn",
				"-cp", classpath, McProtocolWorkerMain.class.getName(),
				Integer.toString(definition.getSupport().getProtocolNumber())
		);

		try {
			return new ProcessBuilder(command).start();
		} catch (IOException exception) {
			throw startupFailure(exception);
		}
	}

	private IllegalStateException startupFailure(Exception cause) {
		return new IllegalStateException("Could not start protocol worker for "
				+ definition.getSupport().getMinecraftVersion() + diagnostics.tail(), cause);
	}

	private void startReaders() {
		String name = "anvil-protocol-" + definition.getSupport().getMinecraftVersion();
		Thread.ofPlatform().daemon().name(name + "-stdout").start(() -> read(process.getInputStream(), this::handleLine));
		Thread.ofPlatform().daemon().name(name + "-stderr").start(() -> read(process.getErrorStream(), diagnostics::remember));
		process.onExit().thenRun(() -> fail(new IllegalStateException("Protocol worker exited with "
				+ process.exitValue() + diagnostics.tail())));
	}

	private void read(InputStream input, Consumer<String> lines) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) lines.accept(line);
		} catch (IOException | RuntimeException exception) {
			if (!process.isAlive()) return;
			diagnostics.remember("Worker output failed: " + exception.getClass().getSimpleName());
			fail(exception);
		}
	}

	private void handleLine(String line) {
		var message = codec.decodeMessage(line);
		if (message.isEmpty()) {
			diagnostics.remember(line);
			return;
		}

		switch (message.get()) {
			case WorkerResponse response -> requests.accept(response);
			case WorkerReady announcement -> acceptReady(announcement);
			case WorkerEvent event -> {
				RemoteProtocolPlayer player = players.get(event.getPlayer());
				if (player != null) player.event(event.getEvent(), event.getPayload());
			}
		}
	}

	private void acceptReady(WorkerReady message) {
		int actual = message.getProtocol();
		if (actual != definition.getSupport().getProtocolNumber()) {
			ready.completeExceptionally(new IllegalStateException("Resolved worker protocol " + actual
					+ " but expected " + definition.getSupport().getProtocolNumber()));
			return;
		}

		workerCapabilities = message.getCapabilities();
		ready.complete(null);
	}

	private void fail(Throwable failure) {
		ready.completeExceptionally(failure);
		requests.fail(failure);
	}

	/**
	 * Requests graceful worker shutdown and escalates to termination if needed.
	 */
	@Override
	public void close() {
		if (!process.isAlive()) return;

		try {
			request(WorkerControlOperation.SHUTDOWN.getWireName(), null,
					JsonNodeFactory.instance.objectNode(), SHUTDOWN_TIMEOUT);
		} catch (RuntimeException ignored) {
			process.destroy();
		}

		try {
			if (!process.waitFor(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS))
				process.destroyForcibly();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
		} finally {
			requests.fail(new IllegalStateException("Protocol worker is closed"));
			players.clear();
		}
	}
}
