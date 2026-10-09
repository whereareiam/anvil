package me.whereareiam.anvil.integration.junit;

import lombok.Getter;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.account.AccountPool;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioEngine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
	@Getter
	private final @Nullable AccountPool accounts;

	private boolean registrationFailed;
	private boolean closed;

	ScenarioInvocation(@NotNull ScenarioEngine engine, @NotNull AnvilScenario scenario, @NotNull BooleanSupplier successful) {
		this(engine, scenario, successful, null);
	}

	/**
	 * Prepares the scenario, checks the account requirement against the prepared context and only then
	 * starts the processes, so a test that lacks accounts is skipped without launching anything.
	 */
	ScenarioInvocation(
			@NotNull ScenarioEngine engine,
			@NotNull AnvilScenario scenario,
			@NotNull BooleanSupplier successful,
			@Nullable AccountRequirement requirement
	) {
		this.engine = engine;
		this.successful = successful;
		try {
			context = engine.prepare(scenario);
			accounts = requirement == null ? null : requirement.satisfy(context.accounts());
			context.start();
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
			try {
				if (accounts != null) accounts.close();
			} finally {
				context.finish(!registrationFailed && successful.getAsBoolean());
			}
		}
	}
}
