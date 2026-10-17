package me.whereareiam.anvil.capability.player;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds one player capability runtime per protocol library for a scenario.
 */
public final class LibraryCapabilityRuntimes {
	private final ClassLoader loader;
	private final Map<String, PlayerCapabilityRuntime> runtimes = new ConcurrentHashMap<>();

	/**
	 * Validates the capability graph of the given libraries now; the runtime of any other library is discovered
	 * when a player first uses it.
	 *
	 * @param libraries protocol libraries the scenario's players select by default
	 */
	public LibraryCapabilityRuntimes(@NotNull Collection<String> libraries) {
		ClassLoader context = Thread.currentThread().getContextClassLoader();
		loader = context == null ? LibraryCapabilityRuntimes.class.getClassLoader() : context;
		for (String library : libraries) of(library);
	}

	/**
	 * Returns the runtime of a protocol library, discovering it on first use.
	 *
	 * @param libraryId protocol library identifier
	 * @return the library's capability runtime
	 */
	public @NotNull PlayerCapabilityRuntime of(@NotNull String libraryId) {
		return runtimes.computeIfAbsent(libraryId, library -> PlayerCapabilityRuntime.discover(loader, library));
	}
}
