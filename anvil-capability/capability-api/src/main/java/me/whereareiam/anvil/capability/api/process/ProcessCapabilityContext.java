package me.whereareiam.anvil.capability.api.process;

import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.platform.ProcessPlatform;
import org.jetbrains.annotations.NotNull;

/**
 * Process identity, the platform's general facts, declared dependencies, and cleanup available during process
 * capability creation. The logical process owns the capability, which follows replacement process generations.
 */
public interface ProcessCapabilityContext extends CapabilityContext<ProcessCapability> {
	/**
	 * Returns the server or proxy name declared in the scenario.
	 *
	 * @return scenario process name
	 */
	@NotNull String processName();

	/**
	 * Returns the platform provider identifier declared by this process.
	 *
	 * @return declared platform identifier
	 */
	@NotNull String platformId();

	/**
	 * Returns what the process's platform agent provides to every capability: console commands and players' joins
	 * and leaves.
	 *
	 * @return the process's platform
	 */
	@NotNull ProcessPlatform platform();
}
