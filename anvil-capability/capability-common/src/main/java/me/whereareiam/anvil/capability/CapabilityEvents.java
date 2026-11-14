package me.whereareiam.anvil.capability;

import me.whereareiam.anvil.capability.api.event.CapabilityEvent;
import me.whereareiam.anvil.capability.api.event.lifecycle.Closed;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Dispatches the lifecycle events one composed owner reports to the actions its capabilities registered, under the
 * rules {@code CapabilityContext.on} documents: an event's actions run in reverse registration order, every one runs,
 * and the first failure is reported with later ones suppressed.
 *
 * <p>Every owner reports {@link Closed}, exactly once; afterwards it reports nothing, and an action registered for
 * {@link Closed} runs at once. Any other event is reported only by owners that support it, as often as the owner
 * reports it, and registering for an event the owner never reports is refused instead of never being called.</p>
 */
final class CapabilityEvents {
	private final String ownerDescription;
	private final Set<Class<? extends CapabilityEvent>> supported;
	private final List<Registration<?>> registrations = new ArrayList<>();
	private boolean closed;

	/**
	 * @param ownerDescription owner identity used in failure diagnostics
	 * @param reported the events the owner reports besides {@link Closed}
	 */
	CapabilityEvents(@NotNull String ownerDescription, @NotNull Set<Class<? extends CapabilityEvent>> reported) {
		this.ownerDescription = ownerDescription;
		Set<Class<? extends CapabilityEvent>> supported = new LinkedHashSet<>(reported);
		supported.add(Closed.class);
		this.supported = Set.copyOf(supported);
	}

	<E extends CapabilityEvent> void on(@NotNull Class<E> event, @NotNull Consumer<E> action) {
		if (!supported.contains(event))
			throw new CapabilityException(ownerDescription + " never reports " + event.getSimpleName()
					+ "; it reports " + supported.stream().map(Class::getSimpleName).sorted().toList());

		synchronized (registrations) {
			if (!closed) {
				registrations.add(new Registration<>(event, action));
				return;
			}
		}

		if (event != Closed.class)
			throw new CapabilityException(ownerDescription + " is closed and reports no further " + event.getSimpleName());
		action.accept(event.cast(Closed.INSTANCE));
	}

	/**
	 * Runs the actions registered for the event, unless the owner is already closed. Reporting {@link Closed} closes
	 * the owner and releases every registration.
	 *
	 * @param event event the owner reports
	 * @param first failure already seen, to which later failures are added as suppressed
	 * @return the first failure, or null when every action completed and none was seen before
	 */
	@Nullable Throwable fire(@NotNull CapabilityEvent event, @Nullable Throwable first) {
		List<Registration<?>> matching = new ArrayList<>();
		synchronized (registrations) {
			if (closed) return first;
			if (!supported.contains(event.getClass()))
				throw new IllegalArgumentException(ownerDescription + " does not report " + event.getClass().getSimpleName());

			for (Registration<?> registration : registrations)
				if (registration.event == event.getClass()) matching.add(registration);
			if (event instanceof Closed) {
				closed = true;
				registrations.clear();
			}
		}

		for (Registration<?> registration : matching.reversed())
			try {
				registration.run(event);
			} catch (RuntimeException | Error failure) {
				if (first == null) first = failure;
				else if (first != failure) first.addSuppressed(failure);
			}

		return first;
	}

	private record Registration<E extends CapabilityEvent>(Class<E> event, Consumer<E> action) {
		void run(CapabilityEvent reported) {
			action.accept(event.cast(reported));
		}
	}
}
