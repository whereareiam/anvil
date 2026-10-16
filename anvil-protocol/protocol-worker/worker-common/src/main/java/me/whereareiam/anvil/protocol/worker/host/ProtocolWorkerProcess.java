package me.whereareiam.anvil.protocol.worker.host;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.protocol.api.model.PlayerRequest;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import me.whereareiam.anvil.protocol.api.worker.model.AuthenticationSession;
import me.whereareiam.anvil.protocol.api.worker.model.WorkerLibrary;
import me.whereareiam.anvil.protocol.worker.child.ProtocolWorker;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerEvent;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerReady;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerResponse;
import me.whereareiam.anvil.protocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.worker.type.WorkerControlOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Owns one isolated worker process shared by players using the same library release. The worker JVM runs
 * {@link ProtocolWorker} for the library on the release's runtime closure followed by the host's class path, and
 * exchanges JSON lines with the host over its private standard input and output.
 */
public final class ProtocolWorkerProcess implements AutoCloseable {
	private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);
	private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(5);

	private final WorkerLibrary library;
	private final ProtocolRelease release;
	private final WorkerMessageCodec codec = new WorkerMessageCodec();
	private final Map<String, RemoteProtocolPlayer> players = new ConcurrentHashMap<>();
	private final CompletableFuture<Void> ready = new CompletableFuture<>();
	private final WorkerDiagnostics diagnostics = new WorkerDiagnostics();

	private final Process process;
	private final WorkerRpcClient requests;
	private volatile Map<String, String> unavailableCapabilities = Map.of();

	/**
	 * Launches and validates the worker of one release before exposing its request channel.
	 *
	 * @param library library the release belongs to
	 * @param release release the worker loads
	 * @param closure the release's verified runtime closure in classpath order
	 * @throws IllegalStateException when the worker would run on a Java version below the release's minimum, its
	 * class path is invalid, or it does not become ready
	 */
	public ProtocolWorkerProcess(@NotNull WorkerLibrary library, @NotNull ProtocolRelease release, @NotNull List<Path> closure) {
		this.library = library;
		this.release = release;
		process = launch(closure);
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

	/**
	 * Returns the library whose release this worker loaded.
	 *
	 * @return the worker's library
	 */
	@NotNull WorkerLibrary library() {
		return library;
	}

	/**
	 * Returns the release this worker loaded, which every player it owns speaks.
	 *
	 * @return the worker's release
	 */
	@NotNull ProtocolRelease release() {
		return release;
	}

	/**
	 * Returns the capabilities the ready worker cannot install, with the reason for each.
	 *
	 * @return immutable reasons keyed by capability ID
	 */
	@NotNull Map<String, String> unavailableCapabilities() {
		return unavailableCapabilities;
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

	/**
	 * Returns the worker's recent output and diagnostics, as failures of this worker report them.
	 *
	 * @return the retained lines after a heading, or an empty string when there are none
	 */
	public @NotNull String diagnosticTail() {
		return diagnostics.tail();
	}

	private Process launch(List<Path> closure) {
		int java = Runtime.version().feature();
		if (java < release.getJavaVersion())
			throw new IllegalStateException(library.getName() + " release " + release.getLibraryVersion() + " requires Java "
					+ release.getJavaVersion() + ", but its worker would run on Java " + java);

		String executable = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
		List<Path> hostEntries = WorkerClasspath.hostEntries(Thread.currentThread().getContextClassLoader());
		List<Path> classpath = new WorkerClasspath(library).resolve(release, closure, hostEntries);
		List<String> command = new ArrayList<>();
		command.add(Path.of(System.getProperty("java.home"), "bin", executable).toString());
		// Standard output carries the worker's messages, so simple logging writes to standard error.
		command.add("-Dorg.slf4j.simpleLogger.logFile=System.err");
		command.add("-Dorg.slf4j.simpleLogger.defaultLogLevel=warn");
		command.addAll(library.getJvmArguments());
		command.addAll(List.of(
				"-cp", String.join(File.pathSeparator, classpath.stream().map(Path::toString).toList()),
				ProtocolWorker.class.getName(),
				library.getId(),
				release.version().toString(),
				release.getEdition().getId(),
				Integer.toString(keyProtocol())
		));

		try {
			return new ProcessBuilder(command).start();
		} catch (IOException exception) {
			throw startupFailure(exception);
		}
	}

	private IllegalStateException startupFailure(Exception cause) {
		return new IllegalStateException("Could not start protocol worker for " + library.getName() + " "
				+ release.getLibraryVersion() + diagnostics.tail(), cause);
	}

	private void startReaders() {
		String name = "anvil-protocol-" + release.version();
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
		if (actual != keyProtocol()) {
			ready.completeExceptionally(new IllegalStateException("Resolved worker protocol " + actual
					+ " but expected " + keyProtocol()));
			return;
		}

		String key = release.version().toString();
		if (!key.equals(message.getRelease())) {
			ready.completeExceptionally(new IllegalStateException("Resolved worker release " + message.getRelease()
					+ " but expected " + key));
			return;
		}

		// Later failures of this worker report the segments it runs with its output.
		diagnostics.remember("[Anvil] Info: the " + library.getName() + " " + release.getLibraryVersion() + " worker selected the segments "
				+ new TreeMap<>(message.getSegments()));
		unavailableCapabilities = Map.copyOf(message.getUnavailable());
		ready.complete(null);
	}

	/**
	 * Returns the protocol number of the release's key version, which the worker verifies against the loaded client.
	 */
	private int keyProtocol() {
		return release.protocolNumber(release.version());
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
