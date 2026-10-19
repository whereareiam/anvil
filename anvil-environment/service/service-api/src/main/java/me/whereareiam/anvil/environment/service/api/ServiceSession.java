package me.whereareiam.anvil.environment.service.api;

import me.whereareiam.anvil.api.model.service.ServiceContainer;
import org.jetbrains.annotations.NotNull;

/**
 * Owns the services of one scenario, from their start until the scenario's processes are gone.
 */
public interface ServiceSession extends AutoCloseable {
	/**
	 * Starts a service and waits until it is ready.
	 *
	 * @param declaration the service
	 * @return the running service, owned by this session
	 * @throws me.whereareiam.anvil.api.exception.ProvisioningException when it cannot be started or does not become
	 *                                                                  ready in time
	 */
	@NotNull ServiceInstance start(@NotNull ServiceContainer declaration);

	/**
	 * Removes every service this session started, including one that failed to start, and releases their ports.
	 */
	@Override
	void close();
}
