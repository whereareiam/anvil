package me.whereareiam.anvil.integration.junit;

import lombok.Getter;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import org.jetbrains.annotations.NotNull;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Owns one JUnit invocation from engine startup until ownership transfers to its extension store.
 */
final class ScenarioInvocation implements AutoCloseable {
	private final @NotNull ScenarioEngine engine;
	@Getter
	private final @NotNull ScenarioContext context;
	private final @NotNull BooleanSupplier successful;

	private boolean registrationFailed;
	private boolean closed;

	ScenarioInvocation(@NotNull ScenarioEngine engine, @NotNull AnvilScenario scenario, @NotNull BooleanSupplier successful) {
		this.engine = engine;
		this.successful = successful;
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
			registrationFailed = true;
			try (this) { throw failure; }
		}
	}

	@Override
	public void close() {
		if (closed) return;
		closed = true;
		try (engine) {
			context.finish(!registrationFailed && successful.getAsBoolean());
		}
	}
}
