package me.whereareiam.anvil.engine;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.scenario.ScenarioExecutor;
import me.whereareiam.anvil.api.scenario.ScenarioExtension;
import me.whereareiam.anvil.engine.scenario.ScenarioSession;
import me.whereareiam.anvil.engine.scenario.ScenarioValidator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns global scenario validation, extension installation, setup, and lifecycle ordering.
 * Scoped functionality is supplied through the global scenario execution boundary.
 */
@RequiredArgsConstructor
public final class AnvilEngine implements ScenarioEngine {
	private final @NotNull EngineOptions options;
	private final @NotNull ScenarioExecutor executor;
	private final @NotNull List<ScenarioExtension> extensions;
	private final @NotNull List<AutoCloseable> resources;

	private final List<ScenarioSession> sessions = new CopyOnWriteArrayList<>();
	private boolean closed;
	private int starting;

	@Override
	public synchronized @NotNull ScenarioContext start(@NotNull AnvilScenario scenario) {
		if (closed) throw new IllegalStateException("Cannot start a scenario after the engine is closed");
		starting++;
		try {
			new ScenarioValidator().validate(scenario, options.isEulaAccepted());
			ScenarioSession session = ScenarioSession.start(scenario, executor, extensions, sessions::remove);
			sessions.add(session);

			if (session.isFinished()) sessions.remove(session);
			return session;
		} finally {
			starting--;
		}
	}

	@Override
	public void close() {
		List<AutoCloseable> closing;
		synchronized (this) {
			if (closed) return;
			if (starting > 0) {
				throw new IllegalStateException("Cannot close the parent engine from a scenario startup callback");
			}

			closed = true;
			closing = new ArrayList<>(sessions);
			closing.addAll(resources.reversed());
		}

		try {
			EngineResources.close(closing);
		} finally {
			sessions.clear();
		}
	}
}
