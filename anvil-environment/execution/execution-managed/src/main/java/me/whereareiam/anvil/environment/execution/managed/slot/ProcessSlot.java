package me.whereareiam.anvil.environment.execution.managed.slot;

import lombok.RequiredArgsConstructor;
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
 * Retains prepared inputs while replacing generation-specific commands and attachments.
 */
@RequiredArgsConstructor
public final class ProcessSlot {
	private final ProcessSpec spec;
	private final ProcessTarget target;
	private final ExecutionPreparation preparation;
	private final Map<String, InetSocketAddress> peers;
	private final Duration startupTimeout;
	private final Duration stopTimeout;
	private @Nullable PreparedProcess prepared;
	private @Nullable PreparedLaunch launch;
	private @Nullable ManagedProcess current;

	public @NotNull String name() {
		return spec.getRequest().getName();
	}

	public void prepare() {
		if (prepared != null) throw new IllegalStateException("Process is already prepared: " + name());
		prepared = preparation.prepare(spec, target, peers);
	}

	public void configure() {
		if (prepared == null) throw new IllegalStateException("Process is not prepared: " + name());
		if (launch != null) throw new IllegalStateException("Process already has a launch: " + name());
		launch = prepared.launch();
	}

	public void start() {
		if (launch == null) throw new IllegalStateException("Process launch is not configured: " + name());
		PreparedLaunch generation = launch;
		current = spec.isProxy()
				? new ManagedProxy(name(), target.address(), spec.getRequest().getWorkspace())
				: new ManagedServer(name(), target.address(), spec.getRequest().getWorkspace());
		current.start(() -> target.start(generation.command()), spec.getReadinessPattern(), spec.getStopCommand(), startupTimeout);
		generation.started();
	}

	public @NotNull ManagedProcess restart() {
		if (current == null) throw new IllegalStateException("Process has not started: " + name());
		stopGeneration();
		configure();
		start();

		return current();
	}

	private void stopGeneration() {
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

	public @NotNull ManagedProcess current() {
		if (current == null) throw new IllegalStateException("Process has not started: " + name());
		return current;
	}

	public void closeLaunch() {
		PreparedLaunch owned = launch;
		launch = null;
		if (owned != null) owned.close();
	}

	public void stopProcess() {
		if (current != null) current.stop(stopTimeout);
	}

	public void finish(boolean successful) {
		PreparedProcess owned = prepared;
		prepared = null;
		if (owned != null) owned.finish(successful);
	}
}
