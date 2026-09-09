package me.whereareiam.anvil.capability;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.capability.Capability;
import me.whereareiam.anvil.api.capability.CapabilityOwner;
import me.whereareiam.anvil.api.exception.CapabilityUnavailableException;
import me.whereareiam.anvil.capability.api.CapabilityContext;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Capabilities and reverse-ordered cleanup belonging to one composed owner.
 * Closing a set releases its registered resources exactly once.
 *
 * @param <C> capability types supported by the owner
 */
public final class CapabilitySet<C extends Capability> implements CapabilityOwner<C>, AutoCloseable {
	private final String ownerDescription;

	private final Map<Class<? extends C>, C> capabilities = new LinkedHashMap<>();
	private final List<Runnable> cleanup = new ArrayList<>();
	private boolean closed;

	CapabilitySet(@NotNull String ownerDescription) {
		this.ownerDescription = ownerDescription;
	}

	@Override
	public @NotNull <T extends C> T capability(@NotNull Class<T> type) {
		C capability = capabilities.get(type);
		if (capability == null)
			throw new CapabilityUnavailableException(ownerDescription + " has no capability " + type.getName()
					+ ". Available: " + capabilities.keySet().stream().map(Class::getName).toList());

		return type.cast(capability);
	}

	@Override
	public boolean hasCapability(@NotNull Class<? extends C> type) {
		return capabilities.containsKey(type);
	}

	<T extends C> void add(@NotNull Class<T> type, @NotNull T capability) {
		capabilities.put(type, capability);
	}

	@NotNull CapabilityContext<C> context(
			@NotNull String providerId,
			@NotNull Set<Class<? extends Capability>> dependencies
	) {
		return new Context(providerId, dependencies);
	}

	/**
	 * Runs every registered action in reverse order. The first unchecked failure is
	 * rethrown after all actions have been attempted, with later failures suppressed.
	 */
	@Override
	public void close() {
		List<Runnable> actions;
		synchronized (cleanup) {
			if (closed) return;
			closed = true;
			actions = new ArrayList<>(cleanup).reversed();
			cleanup.clear();
		}

		Throwable failure = null;
		for (Runnable action : actions)
			try {
				action.run();
			} catch (RuntimeException | Error exception) {
				if (failure == null)
					failure = exception;
				else if (failure != exception)
					failure.addSuppressed(exception);
			}

		if (failure instanceof RuntimeException exception) throw exception;
		if (failure instanceof Error error) throw error;
	}

	private void registerCleanup(@NotNull Runnable action) {
		synchronized (cleanup) {
			if (!closed) {
				cleanup.add(action);
				return;
			}
		}
		action.run();
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
		public void onClose(@NotNull Runnable action) {
			registerCleanup(action);
		}
	}
}
