package me.whereareiam.anvil.engine.scenario;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the running players and processes, closing players before process finalization.
 */
@RequiredArgsConstructor
public final class RunningScenario implements ScenarioContext {
	private final @NotNull AnvilScenario definition;
	private final @NotNull ProcessGroup processes;
	private final @NotNull PlayerManager players;

	private boolean closed;

	@Override
	public @NotNull AnvilScenario definition() {
		return definition;
	}

	@Override
	public @NotNull ScenarioProcesses processes() {
		return processes;
	}

	@Override
	public @NotNull PlayerManager players() {
		return players;
	}

	@Override
	public synchronized void finish(boolean successful) {
		if (closed) return;
		closed = true;

		Throwable failure = cleanup(null, players::close);
		boolean completed = successful && failure == null;
		failure = cleanup(failure, () -> processes.finish(completed));
		if (failure instanceof Error error) throw error;
		if (failure instanceof RuntimeException runtime) throw runtime;
	}

	private @Nullable Throwable cleanup(@Nullable Throwable first, @NotNull Runnable action) {
		try {
			action.run();
		} catch (RuntimeException | Error failure) {
			if (first == null) return failure;
			if (first != failure) first.addSuppressed(failure);
		}

		return first;
	}
}
