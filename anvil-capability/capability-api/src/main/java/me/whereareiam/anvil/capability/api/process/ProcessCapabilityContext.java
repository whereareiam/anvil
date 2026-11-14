package me.whereareiam.anvil.capability.api.process;

import me.whereareiam.anvil.api.process.ProcessCapability;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.event.lifecycle.Closed;
import me.whereareiam.anvil.capability.api.event.lifecycle.ProcessRestarted;
import me.whereareiam.anvil.capability.api.event.scenario.ScenarioFinished;
import me.whereareiam.anvil.capability.api.event.scenario.ScenarioStarted;
import me.whereareiam.anvil.capability.api.platform.ProcessPlatform;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Process identity, the platform's general facts, declared dependencies, and lifecycle events available during process
 * capability creation. The logical process owns the capability, which follows replacement process generations.
 *
 * <p>A process declared with the engine lifetime outlives the scenario that started it and is lent to later
 * scenarios, so its capability serves several scenarios before it is closed. A process reports
 * {@link ScenarioStarted} and {@link ScenarioFinished} for every scenario using it, {@link ProcessRestarted} for every
 * later generation of its JVM, and {@link Closed} once, all through {@link #on(Class, Consumer)}, which lets a
 * capability undo what one scenario did before the next one starts.</p>
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
