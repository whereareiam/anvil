package me.whereareiam.anvil.capability.api;

import me.whereareiam.anvil.api.capability.Capability;
import me.whereareiam.anvil.capability.api.event.CapabilityEvent;
import me.whereareiam.anvil.capability.api.event.lifecycle.Closed;
import me.whereareiam.anvil.capability.api.event.lifecycle.ProcessRestarted;
import me.whereareiam.anvil.capability.api.event.scenario.ScenarioFinished;
import me.whereareiam.anvil.capability.api.event.scenario.ScenarioStarted;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Dependencies and lifecycle events available while creating a capability for one owner.
 * A provider can retrieve only capabilities declared in its descriptor.
 *
 * @param <C> capabilities belonging to the owner
 */
public interface CapabilityContext<C extends Capability> {
	/**
	 * Finds a previously created, explicitly declared dependency on this owner.
	 *
	 * @param type required capability type
	 * @param <T> required capability type
	 * @return the dependency, or empty when unavailable or undeclared
	 */
	@NotNull <T extends C> Optional<T> findCapability(@NotNull Class<T> type);

	/**
	 * Resolves an explicitly declared dependency on this owner.
	 *
	 * @param type required capability type
	 * @param <T> required capability type
	 * @return the dependency
	 * @throws CapabilityException when the capability is unavailable or undeclared
	 */
	default @NotNull <T extends C> T requireCapability(@NotNull Class<T> type) {
		return findCapability(type).orElseThrow(() -> new CapabilityException(
				"Required capability is unavailable or undeclared: " + type.getName()
		));
	}

	/**
	 * Registers an action that runs each time the owner reports the event. This is the only way a capability acts on
	 * its owner's lifecycle, cleanup included: release what the capability holds on {@link Closed}. Every event
	 * follows the same rules:
	 * <ul>
	 *     <li>actions of one event run in reverse registration order, so a capability created later acts
	 *     first;</li>
	 *     <li>every action runs, even when an earlier one failed, and the first failure is reported with the
	 *     later ones added as suppressed;</li>
	 *     <li>a failure fails the step that reported the event, such as the scenario's cleanup.</li>
	 * </ul>
	 * What each event carries, which owners report it, when, and what a failure means beyond that is described on
	 * its type: {@link ScenarioStarted}, {@link ScenarioFinished}, {@link ProcessRestarted} and {@link Closed}.
	 * {@link Closed} actions also run when creating the owner's capabilities fails after registration, and one
	 * registered after the owner closed runs at once. Registration does not transfer ownership of other services
	 * borrowed through an owner-specific context.
	 *
	 * <pre>{@code
	 * Connection connection = connect();
	 * context.on(Closed.class, closed -> connection.close());
	 * context.on(ScenarioFinished.class, finished -> {
	 *     if (finished.isPassed()) removeWhatThisScenarioCreated();
	 * });
	 * }</pre>
	 *
	 * @param event type of the event to act on
	 * @param action action told the event
	 * @param <E> type of the event
	 * @throws CapabilityException when this owner never reports that event, such as {@link ScenarioFinished} for a
	 *                             player
	 */
	<E extends CapabilityEvent> void on(@NotNull Class<E> event, @NotNull Consumer<E> action);
}
