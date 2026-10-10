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
 * Owns one JUnit invocation's scenario from its preparation until ownership transfers to its extension store.
 * The engine is borrowed: it serves every invocation of the test run.
 */
final class ScenarioInvocation implements AutoCloseable {
	@Getter
	private final @NotNull ScenarioContext context;
	private final @NotNull BooleanSupplier successful;
	@Getter
	private final @Nullable AccountPool accounts;
	private final @NotNull OwnedResources resources;

	private boolean registrationFailed;
	private boolean closed;

	ScenarioInvocation(@NotNull ScenarioEngine engine, @NotNull AnvilScenario scenario, @NotNull BooleanSupplier successful) {
		this(engine, scenario, successful, null);
	}

	/**
	 * Prepares the scenario, checks the account requirement against the prepared context and only then
	 * starts the scenario's processes, so a test that lacks accounts is skipped without launching them.
	 */
	ScenarioInvocation(
			@NotNull ScenarioEngine engine,
			@NotNull AnvilScenario scenario,
			@NotNull BooleanSupplier successful,
			@Nullable AccountRequirement requirement
	) {
		this(engine, scenario, successful, requirement, new OwnedResources());
	}

	/**
	 * Takes ownership of the objects the scenario's factory handed over as well. They outlive the scenario's
	 * processes and are closed last, also when the scenario fails to start.
	 */
	ScenarioInvocation(
			@NotNull ScenarioEngine engine,
			@NotNull AnvilScenario scenario,
			@NotNull BooleanSupplier successful,
			@Nullable AccountRequirement requirement,
			@NotNull OwnedResources resources
	) {
		this.successful = successful;
		this.resources = resources;

		ScenarioContext prepared;
		try {
			prepared = engine.prepare(scenario);
		} catch (RuntimeException | Error failure) {
			try (resources) { throw failure; }
		}

		try {
			accounts = requirement == null ? null : requirement.satisfy(prepared.accounts());
			prepared.start();
		} catch (RuntimeException | Error failure) {
			// A scenario that failed to start has finished itself; one that was never started ends normally.
			try (resources; prepared) { throw failure; }
		}
		context = prepared;
	}

	/**
	 * Returns the object of a type that the scenario owns.
	 *
	 * @return the object, or null when the scenario owns none of the type
	 */
	@Nullable Object resource(@NotNull Class<?> type) {
		return resources.find(type);
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
		try (resources) {
			try {
				if (accounts != null) accounts.close();
			} finally {
				context.finish(!registrationFailed && successful.getAsBoolean());
			}
		}
	}
}
