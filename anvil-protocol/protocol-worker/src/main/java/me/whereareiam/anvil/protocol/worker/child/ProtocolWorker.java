package me.whereareiam.anvil.protocol.worker.child;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.protocol.api.model.NativeWorkerContext;
import me.whereareiam.anvil.protocol.worker.model.WorkerLibrary;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerPlayerCapabilities;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerPlayerOptions;
import me.whereareiam.anvil.protocol.worker.model.message.WorkerRequest;
import me.whereareiam.anvil.protocol.worker.transport.WorkerMessageCodec;
import me.whereareiam.anvil.protocol.worker.transport.WorkerMessageWriter;
import me.whereareiam.anvil.protocol.worker.type.WorkerControlOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The library-neutral worker of one release: owns worker players and sequential lifecycle dispatch; capability
 * operations stay in their registry and adapters in the worker's segments.
 *
 * <p>A library's worker entry point hands its arguments to {@link #main(String[], WorkerLibrary, Function)} with
 * the library's description and the way it obtains its native client from the verified segments:</p>
 *
 * <pre>{@code
 * public static void main(String[] arguments) throws Exception {
 *     ProtocolWorker.main(arguments, LIBRARY, segments -> new McProtocolWorkerClient(segments.client(McProtocolClient.class)));
 * }
 * }</pre>
 */
@RequiredArgsConstructor
public final class ProtocolWorker implements AutoCloseable {
	private final @NotNull WorkerClient client;
	private final @NotNull WorkerSegments segments;
	private final @NotNull WorkerCapabilityRegistry capabilities;
	private final @NotNull WorkerMessageWriter responses;

	private final WorkerMessageCodec codec = new WorkerMessageCodec();
	private final Map<String, WorkerPlayer> players = new LinkedHashMap<>();
	private boolean running = true;

	/**
	 * Runs the worker of one release in this JVM: verifies the segments and the loaded release's protocol before
	 * constructing or announcing the worker, then serves requests from standard input until shutdown.
	 *
	 * @param arguments release key version, the release's edition and the native protocol number of the key version,
	 * as the host passes them
	 * @param library library whose release, client segment and capability segments are on the class path
	 * @param clients creates the library's native client from the verified segments
	 * @throws Exception when the loaded runtime does not match or the worker fails
	 */
	public static void main(
			@NotNull String[] arguments,
			@NotNull WorkerLibrary library,
			@NotNull Function<WorkerSegments, WorkerClient> clients
	) throws Exception {
		if (arguments.length != 3)
			throw new IllegalArgumentException("Expected the release key version, its edition and its native protocol number");

		MinecraftVersion version = MinecraftVersion.parse(arguments[0]);
		MinecraftEdition edition = MinecraftEdition.of(arguments[1]);
		int expected = Integer.parseInt(arguments[2]);
		WorkerSegments segments = WorkerSegments.verify(ProtocolWorker.class.getClassLoader(), library, version, System.err::println);
		WorkerClient client = clients.apply(segments);
		int actual = client.protocolNumber();
		if (expected != actual) throw new IllegalStateException("Expected protocol " + expected + " but loaded " + actual);

		NativeWorkerContext context = NativeWorkerContext.builder()
				.libraryId(library.getId())
				.edition(edition)
				.version(version)
				.protocolNumber(actual)
				.nativeSessionType(client.sessionType())
				.build();
		WorkerCapabilityRegistry capabilities = WorkerCapabilityRegistry.discover(context);
		WorkerMessageWriter responses = new WorkerMessageWriter(System.out);
		try (ProtocolWorker worker = new ProtocolWorker(client, segments, capabilities, responses)) {
			responses.ready(version.toString(), actual, segments.selected(), capabilities.capabilities(), capabilities.unavailable());
			worker.run(System.in);
		}
	}

	void run(@NotNull InputStream input) throws IOException {
		try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
			String line;
			while (running && (line = reader.readLine()) != null)
				handle(codec.decodeRequest(line));
		}
	}

	private void handle(WorkerRequest request) {
		try {
			var control = WorkerControlOperation.find(request.getOperation());
			JsonNode result = control.isPresent()
					? execute(control.get(), request)
					: require(request.getPlayer()).execute(request.getOperation(), request.getArguments());
			responses.success(request.getId(), result);
		} catch (Exception | LinkageError | AssertionError failure) {
			responses.failure(request.getId(), failure);
		}
	}

	private JsonNode execute(WorkerControlOperation operation, WorkerRequest request) {
		if (operation == WorkerControlOperation.CREATE_PLAYER) return codec.payload(create(request));

		switch (operation) {
			case DESTROY_PLAYER -> destroy(request.getPlayer());
			case SHUTDOWN -> running = false;
			default -> throw new IllegalStateException("Unhandled control operation: " + operation);
		}
		return JsonNodeFactory.instance.objectNode();
	}

	private WorkerPlayerCapabilities create(WorkerRequest request) {
		String id = requirePlayerId(request.getPlayer());
		if (players.containsKey(id)) throw new IllegalArgumentException("Player already exists: " + id);

		WorkerPlayerOptions options = codec.decodePayload(request.getArguments(), WorkerPlayerOptions.class);
		int port = options.getConnection().getPort();
		if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid worker player options");
		WorkerPlayer player = new WorkerPlayer(
				id,
				options,
				client,
				segments,
				responses,
				capabilities
		);
		WorkerPlayerCapabilities installed = player.initialize();
		players.put(id, player);
		return installed;
	}

	private void destroy(@Nullable String id) {
		WorkerPlayer removed = players.remove(requirePlayerId(id));
		if (removed != null)
			removed.close();
	}

	private WorkerPlayer require(@Nullable String id) {
		WorkerPlayer player = players.get(requirePlayerId(id));
		if (player == null)
			throw new IllegalArgumentException("Unknown worker player: " + id);
		return player;
	}

	private String requirePlayerId(@Nullable String id) {
		if (id == null || id.isBlank())
			throw new IllegalArgumentException("Worker operation requires a player ID");
		return id;
	}

	@Override
	public void close() {
		running = false;
		Throwable failure = null;
		for (WorkerPlayer player : players.values()) {
			try {
				player.close();
			} catch (RuntimeException | Error exception) {
				if (failure == null) failure = exception;
				else if (failure != exception) failure.addSuppressed(exception);
			}
		}
		players.clear();
		if (failure instanceof RuntimeException exception) throw exception;
		if (failure instanceof Error error) throw error;
	}
}
