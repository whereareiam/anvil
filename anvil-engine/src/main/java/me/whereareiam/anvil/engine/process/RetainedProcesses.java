package me.whereareiam.anvil.engine.process;

import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.type.ProcessState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Keeps the process groups that outlive their scenario for the engine and lends each one to one scenario
 * at a time. Scenarios that ask for the same identity take turns on a group; scenarios that run at once
 * each get a group of their own.
 *
 * @param <G> group type of the owner, which may carry what a scenario needs beside the processes
 */
public final class RetainedProcesses<G extends ProcessGroup> implements AutoCloseable {
	private final Map<Object, Deque<G>> idle = new HashMap<>();
	private final Map<G, Object> lent = new IdentityHashMap<>();
	private boolean closed;

	/**
	 * Lends a running group of an identity, starting one when none is idle. An idle group whose processes
	 * are not all ready is stopped instead of being lent.
	 *
	 * @param identity value that is equal for declarations of the same running processes
	 * @param start starts a new group and returns it running; a failed start releases what it acquired
	 * @return group lent to the caller until it is {@link #release released}
	 * @throws IllegalStateException when the engine is closed
	 */
	public @NotNull G lease(@NotNull Object identity, @NotNull Supplier<G> start) {
		G group = idle(identity);
		// Started outside the monitor, so scenarios that run at once start their groups together.
		if (group == null) group = start.get();

		boolean refused;
		synchronized (this) {
			refused = closed;
			if (!refused) lent.put(group, identity);
		}
		if (!refused) return group;

		group.finish(true);
		throw new IllegalStateException("Cannot keep processes for a closed engine");
	}

	/**
	 * Takes a lent group back. It is kept for the next scenario when the scenario that held it succeeded
	 * and left every process ready; otherwise it stops, and after a failure its workspaces are finalized
	 * as failed.
	 *
	 * @param group group returned by {@link #lease}
	 * @param successful whether the scenario and the cleanup of its own processes succeeded
	 */
	public void release(@NotNull G group, boolean successful) {
		boolean reusable = successful && ready(group);
		synchronized (this) {
			Object identity = lent.remove(group);
			if (identity == null) return;

			if (reusable && !closed) {
				idle.computeIfAbsent(identity, ignored -> new ArrayDeque<>()).push(group);
				return;
			}
		}

		group.finish(reusable);
	}

	/**
	 * Stops every idle group. A group that is still lent stops when its scenario releases it.
	 */
	@Override
	public void close() {
		List<G> stopping = new ArrayList<>();
		synchronized (this) {
			if (closed) return;

			closed = true;
			idle.values().forEach(stopping::addAll);
			idle.clear();
		}

		Throwable failure = null;
		for (G group : stopping) failure = stop(group, failure);
		if (failure instanceof Error error) throw error;
		if (failure != null) throw (RuntimeException) failure;
	}

	/**
	 * Takes an idle group whose processes are all ready, stopping any that died while idle.
	 */
	private @Nullable G idle(Object identity) {
		while (true) {
			G group;
			synchronized (this) {
				if (closed) throw new IllegalStateException("Cannot keep processes for a closed engine");

				Deque<G> groups = idle.get(identity);
				group = groups == null ? null : groups.poll();
			}
			if (group == null || ready(group)) return group;

			group.finish(false);
		}
	}

	private static boolean ready(ProcessGroup group) {
		Collection<RunningProcess> processes = group.all();
		return !processes.isEmpty() && processes.stream().allMatch(process -> process.state() == ProcessState.READY);
	}

	/**
	 * Stops one group and returns the first failure of the shutdown so far, with later ones suppressed in it.
	 */
	private static @Nullable Throwable stop(ProcessGroup group, @Nullable Throwable first) {
		try {
			group.finish(true);
			return first;
		} catch (RuntimeException | Error cleanup) {
			if (first == null) return cleanup;
			if (cleanup != first) first.addSuppressed(cleanup);

			return first;
		}
	}
}
