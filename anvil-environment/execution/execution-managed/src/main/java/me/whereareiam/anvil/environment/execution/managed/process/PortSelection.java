package me.whereareiam.anvil.environment.execution.managed.process;

import me.whereareiam.anvil.api.exception.ProvisioningException;
import me.whereareiam.anvil.environment.execution.api.PortReservations;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Reserves listener ports below the operating systems' ephemeral ranges.
 * The process binds its port seconds after preparation selected it. A port from the ephemeral range can be
 * handed to any outgoing connection or other listener in the meantime, so candidates come from a range the
 * operating system does not assign by itself, and a reserved port is not handed out again until released.
 */
public final class PortSelection implements PortReservations {
	/** Linux assigns ephemeral ports from 32768, Windows and macOS from 49152. */
	private static final int FIRST = 20000;
	private static final int LAST = 32767;

	private final int first;
	private final int last;
	private final Set<Integer> reserved = new HashSet<>();

	public PortSelection() {
		this(FIRST, LAST);
	}

	PortSelection(int first, int last) {
		this.first = first;
		this.last = last;
	}

	/**
	 * Reserves a port that can be bound on the address now. The probe socket is closed before returning;
	 * a listener outside this instance may still take the port before the process binds it.
	 *
	 * @param bindAddress local bind address
	 * @return reserved port
	 * @throws ProvisioningException when the address cannot be bound or no candidate is free
	 */
	@Override
	public synchronized int reserve(@NotNull String bindAddress) {
		InetAddress address;
		try {
			address = InetAddress.getByName(bindAddress);
		} catch (IOException failure) {
			throw new ProvisioningException("Could not select a port on " + bindAddress, failure);
		}

		// Starts at a random candidate, so engines in separate JVMs rarely probe the same port at once.
		int candidates = last - first + 1;
		int start = ThreadLocalRandom.current().nextInt(candidates);
		for (int offset = 0; offset < candidates; offset++) {
			int port = first + (start + offset) % candidates;
			if (reserved.contains(port) || !free(address, port)) continue;

			reserved.add(port);
			return port;
		}

		throw new ProvisioningException("Could not find an unassigned port on " + bindAddress);
	}

	@Override
	public synchronized void release(int port) {
		reserved.remove(port);
	}

	private static boolean free(InetAddress address, int port) {
		try (ServerSocket socket = new ServerSocket(port, 1, address)) {
			return socket.isBound();
		} catch (IOException occupied) {
			return false;
		}
	}
}
