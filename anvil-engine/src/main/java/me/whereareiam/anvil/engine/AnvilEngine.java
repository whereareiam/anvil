package me.whereareiam.anvil.engine;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.scenario.ScenarioExtension;
import me.whereareiam.anvil.api.scenario.ScenarioFactory;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import me.whereareiam.anvil.engine.scenario.ObserverGuard;
import me.whereareiam.anvil.engine.scenario.ScenarioSession;
import me.whereareiam.anvil.engine.scenario.ScenarioValidator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Owns global scenario validation, extension installation, setup, and lifecycle ordering.
 * Scoped functionality is supplied through the global scenario execution boundary.
 */
@RequiredArgsConstructor
public final class AnvilEngine implements ScenarioEngine {
	private final @NotNull EngineOptions options;
	private final @NotNull ScenarioFactory scenarioFactory;
	private final @NotNull List<ScenarioExtension> extensions;
	private final @NotNull List<AutoCloseable> resources;

	private final ObserverGuard observers = new ObserverGuard();
	private final List<ScenarioSession> sessions = new CopyOnWriteArrayList<>();
	/**
	 * Scenarios prepare and start under the read lock, so several can do so at once; closing takes the
	 * write lock and therefore waits for them.
	 */
	private final ReentrantReadWriteLock lifecycle = new ReentrantReadWriteLock();
	private boolean closed;

	@Override
	public @NotNull ScenarioContext prepare(@NotNull AnvilScenario scenario, @Nullable ScenarioObserver observer) {
		lifecycle.readLock().lock();
		try {
			if (closed) throw new IllegalStateException("Cannot prepare a scenario after the engine is closed");

			new ScenarioValidator().validate(scenario, options.isEulaAccepted());
			ScenarioObserver observed = observers.wrap(observer);
			ScenarioContext context = Objects.requireNonNull(scenarioFactory.create(scenario, observed), "Scenario factory returned no prepared context");
			ScenarioSession session = ScenarioSession.prepare(context, extensions, sessions::remove, this::startPrepared, observers);
			sessions.add(session);

			return session;
		} finally {
			lifecycle.readLock().unlock();
		}
	}

	private void startPrepared(Runnable start) {
		lifecycle.readLock().lock();
		try {
			if (closed) throw new IllegalStateException("Cannot start a scenario after the engine is closed");

			start.run();
		} finally {
			lifecycle.readLock().unlock();
		}
	}

	@Override
	public void close() {
		observers.reject("close the parent engine");
		// A startup callback holds the read lock, so waiting for the write lock would wait for itself.
		if (lifecycle.getReadHoldCount() > 0)
			throw new IllegalStateException("Cannot close the parent engine from a scenario startup callback");

		List<AutoCloseable> closing;
		lifecycle.writeLock().lock();
		try {
			if (closed) return;

			closed = true;
			closing = new ArrayList<>(sessions);
			closing.addAll(resources.reversed());
		} finally {
			lifecycle.writeLock().unlock();
		}

		try {
			EngineResources.close(closing);
		} finally {
			sessions.clear();
		}
	}
}
