package me.whereareiam.anvil.environment.execution.managed.process;

import me.whereareiam.anvil.api.exception.ProvisioningException;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

/**
 * Selects distinct currently available ports for a scenario's game and agent listeners.
 */
public final class PortSelection {
	private final Set<Integer> selected = new HashSet<>();

	/**
	 * Selects a TCP port not already chosen by this instance. The probe socket is closed
	 * before returning; the child process must bind the port and may encounter an external bind race.
	 *
	 * @param bindAddress local bind address
	 * @return candidate port
	 */
	public synchronized int select(String bindAddress) {
		for (int attempt = 0; attempt < 100; attempt++)
			try (ServerSocket socket = new ServerSocket(0, 50, InetAddress.getByName(bindAddress))) {
				socket.setReuseAddress(false);
				int port = socket.getLocalPort();
				if (selected.add(port))
					return port;
			} catch (IOException e) {
				throw new ProvisioningException("Could not select a port on " + bindAddress, e);
			}

		throw new ProvisioningException("Could not find an unassigned port on " + bindAddress);
	}
}
