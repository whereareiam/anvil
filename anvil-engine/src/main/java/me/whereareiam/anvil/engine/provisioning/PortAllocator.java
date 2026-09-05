package me.whereareiam.anvil.engine.provisioning;

import me.whereareiam.anvil.engine.AnvilException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

/**
 * Allocates distinct currently free ports for one scenario's game and agent listeners.
 */
public final class PortAllocator {
	private final Set<Integer> allocated = new HashSet<>();

	/**
	 * Finds a free TCP port that this allocator has not assigned to another listener.
	 * The caller owns one allocator per scenario and binds each listener after allocation.
	 *
	 * @param bindAddress local bind address
	 * @return free port
	 */
	public synchronized int allocate(String bindAddress) {
		for (int attempt = 0; attempt < 100; attempt++)
			try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName(bindAddress))) {
				socket.setReuseAddress(false);
				int port = socket.getLocalPort();
				if (allocated.add(port))
					return port;
			} catch (IOException e) {
				throw new AnvilException("Could not allocate a port on " + bindAddress, e);
			}
		throw new AnvilException("Could not find an unassigned port on " + bindAddress);
	}
}
