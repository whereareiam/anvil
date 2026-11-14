package me.whereareiam.anvil.capability.api.event.scenario;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.capability.api.event.CapabilityEvent;

/**
 * A scenario has taken the capability's process, before its players are created. A process the scenario starts
 * itself reports it once, when all of the scenario's own processes are ready; a process with the engine lifetime
 * reports it each time it is lent to a scenario, before that scenario's own processes start.
 *
 * <p>A failed action fails the scenario's start, and a kept process whose action failed is stopped instead of
 * being lent to the scenario.</p>
 *
 * <p>Reported to process capabilities only.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScenarioStarted implements CapabilityEvent {
	/**
	 * The only value, since a scenario's start carries nothing a capability does not already know.
	 */
	public static final ScenarioStarted INSTANCE = new ScenarioStarted();

	@Override
	public String toString() {
		return "ScenarioStarted";
	}
}
