package me.whereareiam.anvil.workbench.remote;

import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import me.whereareiam.anvil.workbench.protocol.model.connection.Tunnel;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Makes ports on the workbench's loopback reachable at the same port on the engine's loopback, so a process's
 * address means the same on both machines. Each connection is carried to the workbench through a tunnel of its
 * own, only while it is open.
 */
public final class PortMirror implements Closeable {
	private final @NotNull WorkbenchConnection connection;
	private final @NotNull InetAddress loopback;
	private final List<ServerSocket> listeners = new ArrayList<>();
	private final Set<Integer> mirrored = new HashSet<>();
	private boolean closed;

	/**
	 * Creates a mirror that mirrors nothing yet.
	 *
	 * @param connection connection to the workbench
	 * @param loopback address on the engine's loopback to mirror at, normally {@code 127.0.0.1}
	 */
	public PortMirror(@NotNull WorkbenchConnection connection, @NotNull InetAddress loopback) {
		this.connection = connection;
		this.loopback = loopback;
	}

	/**
	 * Mirrors one address of the workbench; an address that is not on the loopback is the workbench's own concern
	 * and stays as it is.
	 *
	 * @param address address on the workbench
	 * @throws WorkbenchException when the engine's loopback port is taken
	 */
	public synchronized void mirror(@NotNull InetSocketAddress address) {
		if (closed) throw new IllegalStateException("The port mirror is closed");
		if (address.isUnresolved() || address.getAddress() == null || !address.getAddress().isLoopbackAddress()) return;

		int port = address.getPort();
		// A container's main port is its address and one of its listeners too.
		if (!mirrored.add(port)) return;
		ServerSocket listener;
		try {
			listener = new ServerSocket(port, 50, loopback);
		} catch (IOException taken) {
			mirrored.remove(port);
			throw new WorkbenchException("Port " + port + " of the workbench cannot be mirrored, because something on this machine "
					+ "listens on it already", taken);
		}
		listeners.add(listener);
		Thread.ofVirtual().name("anvil-mirror-" + port).start(() -> accept(listener, port));
	}

	private void accept(ServerSocket listener, int port) {
		while (!listener.isClosed()) {
			Socket local;
			try {
				local = listener.accept();
			} catch (IOException closedListener) {
				return;
			}
			Thread.ofVirtual().name("anvil-tunnel-" + port).start(() -> tunnel(local, port));
		}
	}

	private void tunnel(Socket local, int port) {
		try {
			Socket remote = connection.stream(token -> new Tunnel(token, port));
			relay(local.getInputStream(), remote.getOutputStream(), remote);
			relay(remote.getInputStream(), local.getOutputStream(), local);
		} catch (IOException unreachable) {
			try {
				local.close();
			} catch (IOException ignored) {
				// The connection ends either way.
			}
		}
	}

	private static void relay(InputStream input, OutputStream output, Closeable onEnd) {
		Thread.ofVirtual().name("anvil-relay").start(() -> {
			try (onEnd) {
				input.transferTo(output);
			} catch (IOException ignored) {
				// Either end went away; closing the other ends the relay.
			}
		});
	}

	@Override
	public synchronized void close() {
		closed = true;
		for (ServerSocket listener : listeners) {
			try {
				listener.close();
			} catch (IOException ignored) {
				// Closing only stops new connections.
			}
		}
		listeners.clear();
		mirrored.clear();
	}
}
