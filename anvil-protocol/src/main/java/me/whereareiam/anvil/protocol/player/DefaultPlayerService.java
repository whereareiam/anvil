package me.whereareiam.anvil.protocol.player;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.protocol.api.player.PlayerObservationFactory;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayerComposer;
import me.whereareiam.anvil.protocol.api.provider.ProtocolBackend;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProvider;
import me.whereareiam.anvil.protocol.api.provider.ProtocolRuntimeResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lazily creates one selected protocol backend and owns its scenario player managers.
 */
@RequiredArgsConstructor
public final class DefaultPlayerService implements AutoCloseable {
	private final @NotNull ProtocolProvider provider;
	private final @NotNull Path cacheDirectory;
	private final @NotNull ProtocolRuntimeResolver artifacts;
	private final List<RunningPlayerManager> managers = new CopyOnWriteArrayList<>();
	private @Nullable ProtocolBackend backend;
	private boolean closed;

	public synchronized void prepare() {
		ensureOpen();
		if (backend == null) backend = provider.create(cacheDirectory, artifacts);
	}

	public synchronized @NotNull PlayerManager open(
			@NotNull AnvilScenario scenario,
			@NotNull ScenarioProcesses processes,
			@NotNull PlayerObservationFactory observations,
			@NotNull ProtocolPlayerComposer composer
	) {
		prepare();
		RunningPlayerManager manager = new RunningPlayerManager(scenario, backend, processes, observations, composer, managers::remove);
		managers.add(manager);

		return manager;
	}

	@Override
	public synchronized void close() {
		if (closed) return;

		closed = true;
		List<Throwable> failures = new ArrayList<>();
		for (PlayerManager manager : managers) attempt(manager::close, failures);

		managers.clear();
		if (backend != null) attempt(backend::close, failures);
		if (failures.isEmpty()) return;

		Throwable first = failures.getFirst();
		for (Throwable failure : failures.subList(1, failures.size()))
			if (failure != first) first.addSuppressed(failure);

		if (first instanceof Error error) throw error;
		throw (RuntimeException) first;
	}

	private void ensureOpen() {
		if (closed) throw new IllegalStateException("Cannot use the player service after it is closed");
	}

	private void attempt(Runnable action, List<Throwable> failures) {
		try {
			action.run();
		} catch (RuntimeException | Error failure) {
			failures.add(failure);
		}
	}
}
