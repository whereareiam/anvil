package me.whereareiam.anvil.engine.scenario;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.AnvilException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioStartupException;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioAttachment;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import me.whereareiam.anvil.api.scenario.ScenarioExecutor;
import me.whereareiam.anvil.api.scenario.ScenarioExtension;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Applies global scenario additions and setup around a scoped context, owning every attachment.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScenarioSession implements ScenarioContext {
	private final ScenarioContext context;
	private final Consumer<ScenarioSession> onClosed;

	private final List<ScenarioAttachment> attachments = new ArrayList<>();
	private boolean closed;
	@Getter
	private volatile boolean finished;

	/**
	 * Opens scoped functionality, attaches global contributions, then executes scenario setup.
	 * Each successful attachment is owned before the next extension executes. A startup failure
	 * finalizes accepted attachments and the scoped context with a failed outcome.
	 *
	 * @param scenario   validated scenario declaration
	 * @param executor   scoped context factory that owns its own acquisition rollback
	 * @param extensions contributions in installation order
	 * @param onClosed   registration cleanup invoked after all finalization attempts
	 * @return ready session after extensions and setup finish
	 */
	public static @NotNull ScenarioSession start(
			@NotNull AnvilScenario scenario,
			@NotNull ScenarioExecutor executor,
			@NotNull List<ScenarioExtension> extensions,
			@NotNull Consumer<ScenarioSession> onClosed
	) {
		ScenarioContext context = Objects.requireNonNull(executor.open(scenario), "Scenario executor returned no context");
		ScenarioSession session = new ScenarioSession(context, onClosed);
		try {
			for (ScenarioExtension extension : extensions)
				session.attach(extension);
			session.executeSetup();

			return session;
		} catch (RuntimeException | Error failure) {
			try {
				session.finish(false);
			} catch (RuntimeException | Error cleanup) {
				if (cleanup != failure) failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

	@Override
	public @NotNull AnvilScenario definition() {
		return context.definition();
	}

	@Override
	public @NotNull ScenarioProcesses processes() {
		return context.processes();
	}

	@Override
	public @NotNull PlayerManager players() {
		return context.players();
	}

	@Override
	public synchronized void finish(boolean successful) {
		if (closed) return;
		closed = true;
		try {
			release(successful);
		} finally {
			finished = true;
			onClosed.accept(this);
		}
	}

	private void release(boolean successful) {
		List<Throwable> failures = new ArrayList<>();
		for (ScenarioAttachment attachment : attachments.reversed())
			attempt(() -> attachment.finish(successful && failures.isEmpty()), failures);

		attachments.clear();
		attempt(() -> context.finish(successful && failures.isEmpty()), failures);
		if (failures.isEmpty()) return;

		Throwable first = failures.getFirst();
		for (Throwable failure : failures.subList(1, failures.size()))
			if (failure != first) first.addSuppressed(failure);

		if (first instanceof Error error) throw error;
		throw (RuntimeException) first;
	}

	private void attach(ScenarioExtension extension) {
		ScenarioAttachment attachment = Objects.requireNonNull(extension.attach(this), "Scenario extension returned no attachment");
		synchronized (this) {
			if (!closed) {
				attachments.add(attachment);
				return;
			}
		}

		IllegalStateException failure = new IllegalStateException("Scenario was closed while installing an extension");
		try {
			attachment.finish(false);
		} catch (RuntimeException | Error cleanup) {
			if (!cleanup.equals(failure)) failure.addSuppressed(cleanup);
		}

		throw failure;
	}

	private void executeSetup() {
		synchronized (this) {
			if (closed) throw new IllegalStateException("Cannot execute setup for a closed scenario");
		}

		AnvilScenario scenario = definition();
		if (scenario.getSetupHook() == null) return;

		try {
			scenario.getSetupHook().execute(this);
		} catch (AnvilException failure) {
			throw failure;
		} catch (Exception failure) {
			throw new ScenarioStartupException(scenario.getName(), "Setup failed for scenario '" + scenario.getName() + "'", failure);
		}
	}

	private void attempt(Runnable action, List<Throwable> failures) {
		try {
			action.run();
		} catch (RuntimeException | Error failure) {
			failures.add(failure);
		}
	}
}
