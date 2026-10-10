package me.whereareiam.anvil.environment.execution.api;

import org.jetbrains.annotations.NotNull;

/**
 * Hands out listener ports for prepared processes and keeps each one reserved until it is released.
 * A process binds its port long after preparation selected it, so every execution session of an engine
 * reserves through the same instance and concurrent scenarios never receive the same port.
 */
public interface PortReservations {
	/**
	 * Reserves a TCP port that is free on the address now and that no other reservation holds.
	 *
	 * @param bindAddress local address the port will be bound on
	 * @return reserved port
	 */
	int reserve(@NotNull String bindAddress);

	/**
	 * Makes a reserved port available again after its process has stopped for good.
	 * Releasing a port that is not reserved has no effect.
	 *
	 * @param port port returned by {@link #reserve(String)}
	 */
	void release(int port);
}
