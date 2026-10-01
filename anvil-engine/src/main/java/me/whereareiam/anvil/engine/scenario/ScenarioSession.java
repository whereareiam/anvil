package me.whereareiam.anvil.engine.scenario;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.exception.AnvilException;
import me.whereareiam.anvil.api.exception.scenario.ScenarioStartupException;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.player.PlayerManager;
import me.whereareiam.anvil.api.player.account.AccountManager;
import me.whereareiam.anvil.api.process.ScenarioProcesses;
import me.whereareiam.anvil.api.scenario.ScenarioAttachment;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
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
	private final List<ScenarioExtension> extensions;
	private final Consumer<Runnable> startup;
	private final ObserverGuard observers;

	private final List<ScenarioAttachment> attachments = new ArrayList<>();
	private boolean closed;
	private boolean initialized;
	private boolean initializing;
	@Getter
	private volatile boolean finished;

	/**
	 * Owns an explicitly prepared context and defers global contributions until its full start.
	 *
	 * @param context prepared scoped resources
	 * @param extensions ready-only global contributions
	 * @param onClosed registration cleanup
	 * @param startup engine-owned startup serialization and close guard
	 * @param observers engine guard that rejects finishing from a process observer
	 * @return unstarted global session
	 */
	public static @NotNull ScenarioSession prepare(
			@NotNull ScenarioContext context,
			@NotNull List<ScenarioExtension> extensions,
			@NotNull Consumer<ScenarioSession> onClosed,
			@NotNull Consumer<Runnable> startup,
			@NotNull ObserverGuard observers
	) {
		return new ScenarioSession(context, onClosed, extensions, startup, observers);
	}

	@Override
	public void start() {
		startup.accept(this::completeStart);
	}

	private void completeStart() {
		boolean firstStart;
		synchronized (this) {
			if (closed) throw new IllegalStateException("Cannot start a closed scenario");
			if (initializing) throw new IllegalStateException("Scenario startup is already in progress");

			initializing = true;
			firstStart = !initialized;
		}

		// Runs outside the monitor, so another thread can finish the scenario and cancel a slow startup.
		try {
			context.start();
			if (!firstStart) return;

			for (ScenarioExtension extension : extensions) attach(extension);
			executeSetup();
			synchronized (this) {
				initialized = true;
			}
		} catch (RuntimeException | Error failure) {
			try { finish(false); }
			catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
			throw failure;
		} finally {
			synchronized (this) {
				initializing = false;
			}
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
	public @NotNull AccountManager accounts() {
		return context.accounts();
	}

	@Override
	public void finish(boolean successful) {
		// Checked before the monitor: a parallel startup holds it while another thread runs the observer.
		observers.reject("finish the scenario");
		finishOwned(successful);
	}

	private synchronized void finishOwned(boolean successful) {
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
