package me.whereareiam.anvil.workbench.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import me.whereareiam.anvil.workbench.api.Workbench;
import me.whereareiam.anvil.workbench.protocol.WireErrors;
import me.whereareiam.anvil.workbench.protocol.WireLines;
import me.whereareiam.anvil.workbench.protocol.model.message.Request;
import me.whereareiam.anvil.workbench.protocol.model.message.Response;
import me.whereareiam.anvil.workbench.protocol.model.message.ServerMessage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Map;

/**
 * One engine's control connection: requests arrive one per line and run each on a virtual thread of its own, so a
 * long one, such as waiting for a process, never holds up the others; answers carry the request's number. When the
 * connection ends, everything the engine still holds on the workbench is released.
 */
final class EngineSession {
	private final @NotNull Socket socket;
	private final @NotNull ObjectMapper mapper;
	private final @NotNull String owner;
	private final @NotNull String engine;
	private final @NotNull Handles handles;
	private final @NotNull Exposures exposures;
	private final @NotNull EngineOperations operations;
	private final @NotNull OutputStream output;

	EngineSession(
			@NotNull Socket socket,
			@NotNull ObjectMapper mapper,
			@NotNull String owner,
			@NotNull String engine,
			@NotNull Workbench workbench,
			@NotNull ServerSettings settings,
			@NotNull Handles handles,
			@NotNull UploadStore uploads,
			@NotNull Map<String, Socket> waitingReverse
	) throws IOException {
		this.socket = socket;
		this.mapper = mapper;
		this.owner = owner;
		this.engine = engine;
		this.handles = handles;
		this.output = socket.getOutputStream();
		this.exposures = new Exposures(this::send, waitingReverse);
		this.operations = new EngineOperations(workbench, settings, handles, uploads, owner, exposures);
	}

	/**
	 * Serves the engine until its connection ends, then releases what it still holds.
	 */
	void serve() {
		System.out.println("Engine '" + engine + "' connected from " + socket.getRemoteSocketAddress());
		try (socket; exposures) {
			InputStream input = socket.getInputStream();
			Request request;
			while ((request = WireLines.read(mapper, input, Request.class)) != null) {
				Request current = request;
				Thread.ofVirtual().name("anvil-engine-" + engine).start(() -> answer(current));
			}
		} catch (IOException ended) {
			// The engine went away; its objects are released below.
		} finally {
			handles.releaseAll(owner).forEach(failure ->
					System.err.println("Could not release an object of engine '" + engine + "': " + failure));
			System.out.println("Engine '" + engine + "' disconnected");
		}
	}

	private void answer(Request request) {
		try {
			send(new Response(request.getId(), mapper.valueToTree(operations.handle(request.getRequest())), null));
		} catch (Throwable failure) {
			send(new Response(request.getId(), null, WireErrors.describe(failure)));
		}
	}

	private void send(ServerMessage message) {
		try {
			WireLines.write(mapper, output, message);
		} catch (IOException ended) {
			// The engine went away; the reading loop ends the session.
		}
	}
}
