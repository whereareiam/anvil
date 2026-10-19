package me.whereareiam.anvil.environment.execution.managed.process;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Tells when every address of a process reaches a listener, for a process that prints no readiness line.
 * A published container port accepts a connection before anything listens inside the container and closes it right
 * away; a listener keeps it open, so the probe's read waits, or the listener greets.
 */
final class ListenerProbe {
	private static final Duration INTERVAL = Duration.ofMillis(200);

	private final List<InetSocketAddress> addresses;

	ListenerProbe(@NotNull Collection<InetSocketAddress> addresses) {
		this.addresses = List.copyOf(addresses);
	}

	/**
	 * Waits until every address reaches a listener, or until probing should stop.
	 *
	 * @param probing whether to keep probing, such as while the process still starts
	 * @return whether every address reached a listener
	 */
	boolean await(@NotNull BooleanSupplier probing) throws InterruptedException {
		for (InetSocketAddress address : addresses) {
			while (!reachesListener(address)) {
				if (!probing.getAsBoolean()) return false;
				Thread.sleep(INTERVAL);
			}
		}

		return true;
	}

	private static boolean reachesListener(InetSocketAddress address) {
		try (Socket socket = new Socket()) {
			socket.connect(address, (int) INTERVAL.toMillis());
			socket.setSoTimeout((int) INTERVAL.toMillis());

			return greetsOrWaits(socket.getInputStream());
		} catch (IOException refused) {
			return false;
		}
	}

	private static boolean greetsOrWaits(InputStream input) throws IOException {
		try {
			return input.read() >= 0;
		} catch (SocketTimeoutException open) {
			return true;
		}
	}
}
