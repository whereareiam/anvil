package me.whereareiam.anvil.engine;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.engine.EngineBuilder;
import me.whereareiam.anvil.api.engine.EngineExtension;
import me.whereareiam.anvil.api.engine.EngineRegistration;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import me.whereareiam.anvil.api.scenario.ScenarioExtension;
import me.whereareiam.anvil.api.scenario.ScenarioFactory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Installs global contributions around an explicitly supplied scenario factory.
 * The factory is borrowed. Its caller owns the shared resources unless it transfers
 * them to the engine through an extension during build.
 */
@RequiredArgsConstructor
public final class AnvilEngineBuilder implements EngineBuilder {
	private final @NotNull ScenarioFactory scenarioFactory;

	private EngineOptions options = EngineOptions.builder().build();
	private final List<EngineExtension> extensions = new ArrayList<>();
	private boolean consumed;

	@Override
	public synchronized @NotNull EngineBuilder options(@NotNull EngineOptions options) {
		ensureOpen();
		this.options = options;
		return this;
	}

	@Override
	public synchronized @NotNull EngineBuilder extension(@NotNull EngineExtension extension) {
		ensureOpen();
		extensions.add(extension);
		return this;
	}

	@Override
	public synchronized @NotNull ScenarioEngine build() {
		ensureOpen();
		consumed = true;
		Registration registration = new Registration();

		try {
			for (EngineExtension extension : extensions)
				extension.install(registration);

			return registration.build(options, scenarioFactory);
		} catch (RuntimeException | Error failure) {
			try {
				registration.close();
			} catch (RuntimeException | Error cleanup) {
				if (cleanup != failure) failure.addSuppressed(cleanup);
			}

			throw failure;
		} finally {
			extensions.clear();
		}
	}

	private void ensureOpen() {
		if (consumed) throw new IllegalStateException("Engine builder has already been consumed");
	}

	private static final class Registration implements EngineRegistration, AutoCloseable {
		private final List<ScenarioExtension> extensions = new ArrayList<>();
		private final List<AutoCloseable> resources = new ArrayList<>();
		private boolean closed;

		@Override
		public synchronized void scenarios(@NotNull ScenarioExtension extension) {
			ensureOpen();
			extensions.add(extension);
		}

		@Override
		public synchronized void own(@NotNull AutoCloseable resource) {
			ensureOpen();
			if (resources.stream().anyMatch(existing -> existing == resource)) {
				throw new IllegalArgumentException("Engine resource is already registered");
			}

			resources.add(resource);
		}

		private synchronized AnvilEngine build(EngineOptions options, ScenarioFactory scenarioFactory) {
			ensureOpen();

			AnvilEngine engine = new AnvilEngine(options, scenarioFactory, List.copyOf(extensions), List.copyOf(resources));
			closed = true;
			resources.clear();
			extensions.clear();

			return engine;
		}

		@Override
		public synchronized void close() {
			if (closed) return;
			closed = true;

			try {
				EngineResources.close(resources.reversed());
			} finally {
				resources.clear();
				extensions.clear();
			}
		}

		private void ensureOpen() {
			if (closed) throw new IllegalStateException("Engine registrations are closed");
		}
	}
}
