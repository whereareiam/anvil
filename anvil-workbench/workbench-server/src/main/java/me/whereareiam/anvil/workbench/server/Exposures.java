package me.whereareiam.anvil.workbench.server;

import me.whereareiam.anvil.workbench.protocol.model.message.ReverseConnection;
import me.whereareiam.anvil.workbench.protocol.model.message.ServerMessage;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Makes addresses on the engine's machine reachable from the workbench, such as a session server a test runs: the
 * workbench listens on the same loopback port, and every connection to it is carried back to the engine, which
 * opens a reverse stream for it.
 */
final class Exposures implements AutoCloseable {
	private final @NotNull Consumer<ServerMessage> events;
	private final @NotNull Map<String, Socket> waiting;
	private final List<ServerSocket> listeners = new ArrayList<>();
	private final List<String> announced = new ArrayList<>();

	/**
	 * Creates the exposures of one engine connection.
	 *
	 * @param events sends an event to the engine
	 * @param waiting connections waiting for their reverse stream, shared by every engine connection
	 */
	Exposures(@NotNull Consumer<ServerMessage> events, @NotNull Map<String, Socket> waiting) {
		this.events = events;
		this.waiting = waiting;
	}

	/**
	 * Listens on a loopback port and announces every connection to the engine.
	 *
	 * @param port port the engine's service listens on
	 * @throws IOException when the workbench cannot listen on that port
	 */
	synchronized void expose(int port) throws IOException {
		ServerSocket listener = new ServerSocket(port, 50, InetAddress.getLoopbackAddress());
		listeners.add(listener);
		Thread.ofVirtual().name("anvil-expose-" + port).start(() -> {
			while (!listener.isClosed()) {
				try {
					Socket accepted = listener.accept();
					String id = UUID.randomUUID().toString();
					waiting.put(id, accepted);
					synchronized (this) {
						announced.add(id);
					}
					events.accept(new ReverseConnection(id, port));
				} catch (IOException closed) {
					return;
				}
			}
		});
	}

	@Override
	public synchronized void close() {
		for (ServerSocket listener : listeners) {
			try {
				listener.close();
			} catch (IOException ignored) {
				// Closing a listener only stops new connections.
			}
		}
		listeners.clear();
		for (String id : announced) {
			Socket socket = waiting.remove(id);
			if (socket == null) continue;
			try {
				socket.close();
			} catch (IOException ignored) {
				// The connection ends either way.
			}
		}
		announced.clear();
	}
}
