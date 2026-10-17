package me.whereareiam.anvil.capability.api.history;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Read position in a history that only grows, such as the messages a player received, for capabilities that hand
 * out watches. A cursor starts at the end of the history when it is created, so it never sees what happened
 * before; {@link #take} moves it past the entry it finds, so the next search continues after it.
 *
 * <pre>{@code
 * HistoryCursor<String> cursor = new HistoryCursor<>(this::history);
 * channel.await(() -> cursor.find(matcher) != null, "receive a matching message", timeout);
 * String message = cursor.take(matcher);
 * }</pre>
 *
 * @param <T> history entry
 */
public final class HistoryCursor<T> {
	private final Supplier<List<T>> history;
	private int position;

	/**
	 * Creates a cursor at the current end of a history.
	 *
	 * @param history supplies the whole history, oldest entry first; an entry once listed keeps its position
	 */
	public HistoryCursor(@NotNull Supplier<List<T>> history) {
		this.history = history;
		this.position = history.get().size();
	}

	/**
	 * Finds the first entry after the cursor that satisfies a condition, without moving the cursor.
	 *
	 * @param matcher condition on an entry
	 * @return the first matching entry, or null when none has been recorded yet
	 */
	public synchronized @Nullable T find(@NotNull Predicate<T> matcher) {
		List<T> entries = history.get();
		for (int index = position; index < entries.size(); index++)
			if (matcher.test(entries.get(index))) return entries.get(index);

		return null;
	}

	/**
	 * Finds the first entry after the cursor that satisfies a condition and moves the cursor directly after it.
	 *
	 * @param matcher condition on an entry
	 * @return the first matching entry, or null when none has been recorded yet, which leaves the cursor in place
	 */
	public synchronized @Nullable T take(@NotNull Predicate<T> matcher) {
		List<T> entries = history.get();
		for (int index = position; index < entries.size(); index++) {
			if (!matcher.test(entries.get(index))) continue;

			position = index + 1;
			return entries.get(index);
		}

		return null;
	}

	/**
	 * Returns the entries after the cursor without moving it.
	 *
	 * @return entries recorded after the cursor, oldest first
	 */
	public synchronized @NotNull List<T> unread() {
		List<T> entries = history.get();
		return List.copyOf(entries.subList(Math.min(position, entries.size()), entries.size()));
	}
}
