package me.whereareiam.anvil.environment.service.api;

import org.jetbrains.annotations.NotNull;

/**
 * Hands out the host ports services publish on, shared with whatever else of the engine listens on host ports, so
 * a service never takes a port reserved for a process.
 */
public interface HostPorts {
	/**
	 * Reserves a port that is free on the address and that nothing else of the engine holds.
	 *
	 * @param bindAddress the host address the port is published on
	 * @return the reserved port
	 */
	int reserve(@NotNull String bindAddress);

	/**
	 * Makes a reserved port available again once its service is gone.
	 *
	 * @param port a port {@link #reserve(String)} returned
	 */
	void release(int port);
}
