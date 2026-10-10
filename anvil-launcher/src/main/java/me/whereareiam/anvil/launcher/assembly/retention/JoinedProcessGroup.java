package me.whereareiam.anvil.launcher.assembly.retention;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.process.type.RunningProxy;
import me.whereareiam.anvil.api.process.type.RunningServer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Presents a scenario's own processes and the retained processes it holds as one group.
 * The scenario's own processes are finalized with it; the retained ones are handed back.
 */
@RequiredArgsConstructor
final class JoinedProcessGroup implements ProcessGroup {
	private final @NotNull ProcessGroup own;
	private final @NotNull ProcessGroup retained;
	private final @NotNull Set<String> retainedNames;
	/**
	 * Hands the retained processes back, told whether the scenario and its cleanup succeeded.
	 */
	private final @NotNull Consumer<Boolean> release;

	private boolean closed;

	@Override
	public void startAll() {
		// A retained process the scenario stopped starts again before the processes that connect to it.
		retained.startAll();
		own.startAll();
	}

	@Override
	public @NotNull Collection<RunningProcess> all() {
		return both(ScenarioProcesses::all);
	}

	@Override
	public @NotNull RunningProcess get(@NotNull String name) {
		return owner(name).get(name);
	}

	@Override
	public @NotNull Collection<RunningServer> servers() {
		return both(ScenarioProcesses::servers);
	}

	@Override
	public @NotNull RunningServer server(@NotNull String name) {
		return owner(name).server(name);
	}

	@Override
	public @NotNull Collection<RunningProxy> proxies() {
		return both(ScenarioProcesses::proxies);
	}

	@Override
	public @NotNull RunningProxy proxy(@NotNull String name) {
		return owner(name).proxy(name);
	}

	@Override
	public @NotNull RunningProcess start(@NotNull String name) {
		return change(name, ScenarioProcesses::start);
	}

	@Override
	public void stop(@NotNull String name) {
		change(name, (processes, process) -> {
			processes.stop(process);
			return null;
		});
	}

	@Override
	public @NotNull RunningProcess restart(@NotNull String name) {
		return change(name, ScenarioProcesses::restart);
	}

	@Override
	public void finish(boolean successful) {
		synchronized (this) {
			if (closed) return;
			closed = true;
		}

		boolean completed = false;
		try {
			own.finish(successful);
			completed = successful;
		} finally {
			release.accept(completed);
		}
	}

	private @NotNull ScenarioProcesses owner(@NotNull String name) {
		return retainedNames.contains(name) ? retained : own;
	}

	/**
	 * A retained process outlives this group, so the group itself refuses changes once the scenario finished.
	 */
	private <T> T change(@NotNull String name, @NotNull BiFunction<ScenarioProcesses, String, T> action) {
		synchronized (this) {
			if (closed) throw new IllegalStateException("Cannot change processes after scenario cleanup");
		}

		return action.apply(owner(name), name);
	}

	private <P> @NotNull Collection<P> both(@NotNull Function<ScenarioProcesses, Collection<P>> lookup) {
		List<P> processes = new ArrayList<>(lookup.apply(retained));
		processes.addAll(lookup.apply(own));

		return List.copyOf(processes);
	}
}
