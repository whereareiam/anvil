package me.whereareiam.anvil.integration.junit;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.extension.ExtensionConfigurationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The objects one scenario owns, found by parameter type and closed in reverse order of ownership.
 */
final class OwnedResources implements ScenarioResources, AutoCloseable {
	private final List<Object> resources = new ArrayList<>();

	@Override
	public synchronized <T> @NotNull T own(@NotNull T resource) {
		resources.add(Objects.requireNonNull(resource, "resource"));
		return resource;
	}

	/**
	 * Returns the one owned object of a type.
	 *
	 * @return the object, or null when the scenario owns none of the type
	 * @throws ExtensionConfigurationException when several owned objects are of the type
	 */
	synchronized @Nullable Object find(@NotNull Class<?> type) {
		List<Object> matches = resources.stream().filter(type::isInstance).toList();
		if (matches.size() > 1)
			throw new ExtensionConfigurationException("The scenario owns several resources of type " + type.getName()
					+ "; request a more specific type");

		return matches.isEmpty() ? null : matches.getFirst();
	}

	/**
	 * Closes every closeable object, keeping the first failure and suppressing later ones.
	 */
	@Override
	public synchronized void close() {
		RuntimeException failure = null;
		for (Object resource : resources.reversed()) {
			if (!(resource instanceof AutoCloseable closeable)) continue;
			try {
				closeable.close();
			} catch (Exception exception) {
				RuntimeException wrapped = exception instanceof RuntimeException runtime
						? runtime
						: new IllegalStateException("Could not close scenario resource " + resource, exception);
				if (failure == null) failure = wrapped;
				else failure.addSuppressed(wrapped);
			}
		}
		resources.clear();
		if (failure != null) throw failure;
	}
}
