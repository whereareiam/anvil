package me.whereareiam.anvil.engine;

import me.whereareiam.anvil.api.exception.AnvilException;

import java.util.ArrayList;
import java.util.List;

/**
 * Releases independently owned resources while retaining the first failure and all later causes.
 */
final class EngineResources {
	static void close(List<? extends AutoCloseable> resources) {
		List<Throwable> failures = new ArrayList<>();
		for (AutoCloseable resource : resources)
			try {
				resource.close();
			} catch (Throwable failure) {
				if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
				failures.add(failure);
			}

		if (failures.isEmpty()) return;

		Throwable first = failures.getFirst();
		for (Throwable failure : failures.subList(1, failures.size()))
			if (failure != first) first.addSuppressed(failure);

		if (first instanceof Error error) throw error;
		if (first instanceof RuntimeException runtime) throw runtime;

		throw new AnvilException("Could not release engine resources", first);
	}
}
