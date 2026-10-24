package me.whereareiam.anvil.workbench.server;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The objects one engine holds on the workbench, such as execution sessions, process targets, running processes and
 * prepared workspaces, by the random handle the engine names them with. A stream connection finds its object here
 * and must present the token of the engine that owns it.
 */
final class Handles {
	private final Map<String, Entry> entries = new ConcurrentHashMap<>();

	/**
	 * Registers an object for an engine.
	 *
	 * @param owner token of the engine that owns it
	 * @param value object
	 * @param release how to release it when the engine goes away without doing so itself
	 * @return the object's handle
	 */
	@NotNull String register(@NotNull String owner, @NotNull Object value, @NotNull AutoCloseable release) {
		String handle = UUID.randomUUID().toString();
		entries.put(handle, new Entry(owner, value, release));
		return handle;
	}

	/**
	 * Returns an engine's object of a type.
	 *
	 * @param owner token of the asking engine
	 * @param handle handle the engine named
	 * @param type expected type
	 * @return the object
	 * @throws IllegalArgumentException when the engine holds no such object
	 */
	<T> @NotNull T get(@NotNull String owner, @NotNull String handle, @NotNull Class<T> type) {
		Entry entry = entries.get(handle);
		if (entry == null || !entry.owner().equals(owner) || !type.isInstance(entry.value()))
			throw new IllegalArgumentException("The workbench holds no " + type.getSimpleName() + " " + handle + " for this engine");

		return type.cast(entry.value());
	}

	/**
	 * Forgets an object its engine released itself.
	 *
	 * @param owner token of the engine
	 * @param handle handle of the object
	 * @return the object, or null when the engine held none
	 */
	@Nullable Object remove(@NotNull String owner, @NotNull String handle) {
		Entry entry = entries.get(handle);
		if (entry == null || !entry.owner().equals(owner)) return null;

		entries.remove(handle);
		return entry.value();
	}

	/**
	 * Releases everything an engine still holds, newest first, after its control connection ended.
	 *
	 * @param owner token of the engine
	 * @return failures of the releases, each attempted
	 */
	@NotNull List<Exception> releaseAll(@NotNull String owner) {
		List<Map.Entry<String, Entry>> owned = new ArrayList<>(entries.entrySet().stream()
				.filter(entry -> entry.getValue().owner().equals(owner)).toList());
		Collections.reverse(owned);

		List<Exception> failures = new ArrayList<>();
		for (Map.Entry<String, Entry> entry : owned) {
			entries.remove(entry.getKey());
			try {
				entry.getValue().release().close();
			} catch (Exception failure) {
				failures.add(failure);
			}
		}
		return failures;
	}

	private record Entry(String owner, Object value, AutoCloseable release) {
	}
}
