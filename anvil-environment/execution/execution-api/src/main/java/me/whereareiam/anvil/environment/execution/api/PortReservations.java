package me.whereareiam.anvil.environment.execution.api;

import me.whereareiam.anvil.api.type.network.ListenerTransport;
import org.jetbrains.annotations.NotNull;

/**
 * Hands out listener ports for prepared processes and keeps each one reserved until it is released.
 * A process binds its port long after preparation selected it, so every execution session of an engine
 * reserves through the same instance and concurrent scenarios never receive the same port.
 */
public interface PortReservations {
	/**
	 * Reserves a port that is free for the transport on the address now and that no other reservation holds.
	 * Reserved numbers stay distinct across transports, so a released TCP port and a UDP port never share one.
	 *
	 * @param bindAddress local address the port will be bound on
	 * @param transport transport the port will be bound with
	 * @return reserved port
	 */
	int reserve(@NotNull String bindAddress, @NotNull ListenerTransport transport);

	/**
	 * Makes a reserved port available again after its process has stopped for good.
	 * Releasing a port that is not reserved has no effect.
	 *
	 * @param port port returned by {@link #reserve(String, ListenerTransport)}
	 */
	void release(int port);
}
