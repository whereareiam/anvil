package me.whereareiam.anvil.environment.service.api;

import me.whereareiam.anvil.environment.service.api.model.ServiceContext;
import org.jetbrains.annotations.NotNull;

/**
 * Runs the service containers of scenarios, such as a database the scenario's processes connect to. Installed
 * providers are found through {@link java.util.ServiceLoader}.
 */
public interface ServiceProvider {
	/**
	 * Returns the identifier the provider is selected by.
	 *
	 * @return provider identifier
	 */
	@NotNull String id();

	/**
	 * Opens the services of one scenario, owned by the caller until it closes them.
	 *
	 * @param context where the services publish their ports and which network they join
	 * @return caller-owned session
	 */
	@NotNull ServiceSession open(@NotNull ServiceContext context);
}
