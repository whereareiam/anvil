package me.whereareiam.anvil.engine.scenario;

import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Marks threads that run process-observer callbacks and rejects lifecycle calls from them.
 * <p>
 * An observer runs inside a process start, before its launch. Finishing the scenario or closing the
 * engine from that callback would wait for the start that is running it, so both are rejected; the
 * resulting exception aborts startup through the observer contract.
 */
public final class ObserverGuard {
	private final ThreadLocal<Boolean> observing = ThreadLocal.withInitial(() -> false);

	/**
	 * Wraps an observer so its callbacks mark the calling thread for their duration.
	 *
	 * @param observer caller observer, or null when nothing observes the scenario
	 * @return marking observer, or null when none was supplied
	 */
	public @Nullable ScenarioObserver wrap(@Nullable ScenarioObserver observer) {
		if (observer == null) return null;

		return process -> {
			boolean previous = observing.get();
			observing.set(true);

			try {
				observer.processCreated(process);
			} finally {
				if (previous) observing.set(true);
				else observing.remove();
			}
		};
	}

	/**
	 * Rejects a lifecycle action requested from a process observer.
	 *
	 * @param action description of the rejected action, such as "finish the scenario"
	 * @throws IllegalStateException when the current thread is running an observer callback
	 */
	public void reject(@NotNull String action) {
		if (observing.get()) throw new IllegalStateException("Cannot " + action + " from a process observer");
	}
}
