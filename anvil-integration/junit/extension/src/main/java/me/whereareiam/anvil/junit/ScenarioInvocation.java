package me.whereareiam.anvil.junit;

import lombok.Getter;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Owns one JUnit invocation from engine startup until ownership transfers to its extension store.
 */
final class ScenarioInvocation implements AutoCloseable {
	private final @NotNull ScenarioEngine engine;
	@Getter
	private final @NotNull ScenarioContext context;

	ScenarioInvocation(@NotNull ScenarioEngine engine, @NotNull AnvilScenario scenario) {
		this.engine = engine;
		try {
			context = engine.start(scenario);
		} catch (RuntimeException | Error failure) {
			try (engine) { throw failure; }
		}
	}

	void register(@NotNull Consumer<ScenarioInvocation> registration) {
		try {
			registration.accept(this);
		} catch (RuntimeException | Error failure) {
			try (this) { throw failure; }
		}
	}

	@Override
	public void close() {
		try (engine; context) {
			// Reverse resource order closes the context first and preserves both cleanup failures.
		}
	}
}
