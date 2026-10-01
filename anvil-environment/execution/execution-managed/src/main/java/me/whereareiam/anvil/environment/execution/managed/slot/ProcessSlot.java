package me.whereareiam.anvil.environment.execution.managed.slot;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.scenario.ScenarioObserver;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessSpec;
import me.whereareiam.anvil.environment.execution.api.preparation.ExecutionPreparation;
import me.whereareiam.anvil.environment.execution.api.preparation.PreparedLaunch;
import me.whereareiam.anvil.environment.execution.api.preparation.PreparedProcess;
import me.whereareiam.anvil.environment.execution.api.process.ProcessTarget;
import me.whereareiam.anvil.environment.execution.managed.process.ManagedProcess;
import me.whereareiam.anvil.environment.execution.managed.process.type.ManagedProxy;
import me.whereareiam.anvil.environment.execution.managed.process.type.ManagedServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Retains prepared inputs while owning commands and attachments for each process start.
 */
@RequiredArgsConstructor
public final class ProcessSlot {
	private final ProcessSpec spec;
	private final ProcessTarget target;
	private final ExecutionPreparation preparation;
	private final Map<String, InetSocketAddress> peers;
	private final Duration startupTimeout;
	private final Duration stopTimeout;
	private final @Nullable ScenarioObserver observer;

	private @Nullable PreparedProcess prepared;
	private @Nullable PreparedLaunch launch;

	private volatile @Nullable ManagedProcess current;
	private volatile boolean failed;
	private volatile boolean cancelled;

	public @NotNull String name() {
		return spec.getRequest().getName();
	}

	public void prepare() {
		if (prepared != null) throw new IllegalStateException("Process is already prepared: " + name());
		prepared = preparation.prepare(spec, target, peers);
	}

	public synchronized @NotNull ManagedProcess start() {
		if (prepared == null) throw new IllegalStateException("Process is not prepared: " + name());
		if (current != null && current.state() == ProcessState.READY) return current;

		if (cancelled) throw new IllegalStateException("Cannot start process '" + name() + "' while the scenario is finishing");

		try {
			if (current != null && (launch != null || current.state() != ProcessState.STOPPED)) stopGeneration();
			PreparedLaunch preparedLaunch = prepared.launch();
			launch = preparedLaunch;
			current = spec.isProxy()
					? new ManagedProxy(name(), target.address(), spec.getRequest().getWorkspace(), prepared.capabilities())
					: new ManagedServer(name(), target.address(), spec.getRequest().getWorkspace(), prepared.capabilities());

			// A cancellation that read the previous generation is caught here; a later one sees this one.
			if (cancelled) current.cancelStart();
			if (observer != null) observer.processCreated(current);

			current.start(() -> target.start(
							preparedLaunch.command()),
					spec.getReadinessPattern(),
					spec.getStopCommand(),
					startupTimeout
			);
			preparedLaunch.started();

			return current;
		} catch (RuntimeException | Error failure) {
			try {
				stopGeneration();
			} catch (RuntimeException | Error cleanup) {
				if (cleanup != failure) failure.addSuppressed(cleanup);
			}
			throw failure;
		}
	}

	public synchronized @NotNull ManagedProcess restart() {
		if (current == null) throw new IllegalStateException("Process has not started: " + name());
		stopGeneration();
		return start();
	}

	public synchronized void stopGeneration() {
		Throwable failure = null;
		for (Runnable action : List.<Runnable>of(this::closeLaunch, this::stopProcess)) {
			try {
				action.run();
			} catch (RuntimeException | Error cleanup) {
				if (failure == null) failure = cleanup;
				else if (cleanup != failure) failure.addSuppressed(cleanup);
			}
		}
		if (failure instanceof Error error) throw error;
		if (failure != null) throw (RuntimeException) failure;
	}

	/**
	 * Abandons any start in progress without waiting for the slot, and rejects later starts.
	 */
	public void cancelStart() {
		cancelled = true;
		ManagedProcess process = current;
		if (process != null) process.cancelStart();
	}

	public @NotNull ManagedProcess current() {
		if (current == null) throw new IllegalStateException("Process has not started: " + name());
		return current;
	}

	public @Nullable ManagedProcess currentOrNull() {
		return current;
	}

	public void closeLaunch() {
		PreparedLaunch owned = launch;
		launch = null;
		if (owned != null) owned.close();
	}

	public void stopProcess() {
		ManagedProcess process = current;
		if (process == null) return;

		try {
			process.stop(stopTimeout);
		} finally {
			failed |= process.failed();
		}
	}

	public boolean failed() {
		ManagedProcess process = current;
		return failed || process != null && process.failed();
	}

	public void finish(boolean successful) {
		PreparedProcess owned = prepared;
		prepared = null;
		if (owned != null) owned.finish(successful);
	}
}
