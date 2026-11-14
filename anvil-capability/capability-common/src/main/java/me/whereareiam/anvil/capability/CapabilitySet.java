package me.whereareiam.anvil.capability;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.capability.Capability;
import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.event.CapabilityEvent;
import me.whereareiam.anvil.capability.api.event.lifecycle.Closed;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Capabilities and the lifecycle events of one composed owner. Closing a set reports {@link Closed} exactly once,
 * which releases its registered resources.
 *
 * @param <C> capability types supported by the owner
 */
public final class CapabilitySet<C extends Capability> implements CapabilityOwner<C>, AutoCloseable {
	private final String ownerDescription;

	private final Map<Class<? extends C>, C> capabilities = new LinkedHashMap<>();
	private final Map<Class<? extends C>, String> unavailable = new LinkedHashMap<>();
	private final CapabilityEvents events;

	CapabilitySet(@NotNull String ownerDescription, @NotNull Set<Class<? extends CapabilityEvent>> events) {
		this.ownerDescription = ownerDescription;
		this.events = new CapabilityEvents(ownerDescription, events);
	}

	@Override
	public @NotNull <T extends C> T capability(@NotNull Class<T> type) {
		C capability = capabilities.get(type);
		String reason = unavailable.get(type);
		if (capability == null && reason != null)
			throw new CapabilityUnavailableException(ownerDescription + " has no capability " + type.getName() + ": " + reason);
		if (capability == null)
			throw new CapabilityUnavailableException(ownerDescription + " has no capability " + type.getName()
					+ ". Available: " + capabilities.keySet().stream().map(Class::getName).toList());

		return type.cast(capability);
	}

	@Override
	public boolean hasCapability(@NotNull Class<? extends C> type) {
		return capabilities.containsKey(type);
	}

	/**
	 * Returns the capabilities skipped for this owner, with the reason for each.
	 *
	 * @return immutable reasons keyed by capability type, in composition order
	 */
	public @NotNull Map<Class<? extends C>, String> unavailable() {
		return Collections.unmodifiableMap(new LinkedHashMap<>(unavailable));
	}

	<T extends C> void add(@NotNull Class<T> type, @NotNull T capability) {
		capabilities.put(type, capability);
	}

	void unavailable(@NotNull Class<? extends C> type, @NotNull String reason) {
		unavailable.put(type, reason);
	}

	@NotNull CapabilityContext<C> context(
			@NotNull String providerId,
			@NotNull Set<Class<? extends Capability>> dependencies
	) {
		return new Context(providerId, dependencies);
	}

	/**
	 * Reports an event to the actions the capabilities registered for it, under the rules of
	 * {@link CapabilityContext#on(Class, Consumer)}. Nothing runs once the set is closed.
	 *
	 * @param event event the owner reports, other than {@link Closed}, which {@link #close()} reports
	 * @param first failure already seen, to which later failures are added as suppressed
	 * @return the first failure, or null when every action completed and none was seen before
	 * @throws IllegalArgumentException when the owner does not report the event
	 */
	public @Nullable Throwable report(@NotNull CapabilityEvent event, @Nullable Throwable first) {
		if (event instanceof Closed) throw new IllegalArgumentException("Close the capability set to report Closed");
		return events.fire(event, first);
	}

	/**
	 * Reports {@link Closed}. The first unchecked failure is rethrown after all actions have been attempted, with
	 * later failures suppressed.
	 */
	@Override
	public void close() {
		Throwable failure = events.fire(Closed.INSTANCE, null);
		if (failure instanceof RuntimeException exception) throw exception;
		if (failure instanceof Error error) throw error;
	}

	@RequiredArgsConstructor
	private final class Context implements CapabilityContext<C> {
		private final String providerId;
		private final Set<Class<? extends Capability>> dependencies;

		@Override
		public @NotNull <T extends C> Optional<T> findCapability(@NotNull Class<T> type) {
			if (!dependencies.contains(type)) return Optional.empty();
			return Optional.ofNullable(capabilities.get(type)).map(type::cast);
		}

		@Override
		public @NotNull <T extends C> T requireCapability(@NotNull Class<T> type) {
			return findCapability(type).orElseThrow(() -> new CapabilityException(
					"Provider '" + providerId + "' for " + ownerDescription
							+ " cannot access unavailable or undeclared capability " + type.getName()
			));
		}

		@Override
		public <E extends CapabilityEvent> void on(@NotNull Class<E> event, @NotNull Consumer<E> action) {
			events.on(event, action);
		}
	}
}
