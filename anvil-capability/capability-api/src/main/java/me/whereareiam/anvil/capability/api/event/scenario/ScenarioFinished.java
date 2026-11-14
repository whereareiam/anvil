package me.whereareiam.anvil.capability.api.event.scenario;

import lombok.Value;
import me.whereareiam.anvil.capability.api.event.CapabilityEvent;
import me.whereareiam.anvil.capability.api.event.lifecycle.Closed;

/**
 * A scenario using the capability's process has finished: its players are closed, and its own processes are not
 * stopped yet nor is a process kept beyond it handed back. A process the scenario starts itself reports it once,
 * just before {@link Closed}; a process with the engine lifetime reports it for every scenario it is lent to, and
 * not when the engine finally stops it.
 *
 * <p>A scenario that failed keeps its processes' workspaces for diagnostics, so an action should normally leave
 * its state in place then. A failed action fails the scenario's cleanup, and a kept process whose action failed is
 * stopped instead of being lent to the next scenario. An action can find the process stopped, in which case its
 * requests report {@code ChannelUnavailableException}.</p>
 *
 * <p>Reported to process capabilities only.</p>
 */
@Value
public class ScenarioFinished implements CapabilityEvent {
	/**
	 * Whether the scenario passed.
	 */
	boolean passed;
}
