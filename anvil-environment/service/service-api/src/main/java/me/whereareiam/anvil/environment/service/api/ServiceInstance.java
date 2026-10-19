package me.whereareiam.anvil.environment.service.api;

import me.whereareiam.anvil.api.service.RunningService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;

/**
 * A started service: what the test sees of it, plus where processes inside the joined network reach it.
 */
public interface ServiceInstance extends RunningService {
	/**
	 * Returns where a process inside the {@link me.whereareiam.anvil.environment.service.api.model.ServiceContext#getNetwork()
	 * joined network} reaches one of the service's container ports.
	 *
	 * @param containerPort a port the service declared
	 * @return the address inside the joined network, or null when the session joined none
	 * @throws IllegalArgumentException when the service did not declare the port
	 */
	@Nullable InetSocketAddress networkAddress(int containerPort);

	/**
	 * Returns the address the test, and processes on the host, reach one of the service's container ports at.
	 *
	 * @param containerPort a port the service declared
	 * @return the host address
	 * @throws IllegalArgumentException when the service did not declare the port
	 */
	@Override
	@NotNull InetSocketAddress address(int containerPort);
}
