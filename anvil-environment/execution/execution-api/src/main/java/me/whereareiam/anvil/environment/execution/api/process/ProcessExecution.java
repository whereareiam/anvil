package me.whereareiam.anvil.environment.execution.api.process;

import me.whereareiam.anvil.api.process.type.RunningContainer;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Controls one execution generation independently of its platform and readiness rules.
 */
public interface ProcessExecution {
	/**
	 * Returns combined standard output and error for console capture.
	 *
	 * @return captured output stream
	 */
	@NotNull InputStream output();

	/**
	 * Returns standard input for platform console commands.
	 *
	 * @return command stream
	 */
	@NotNull OutputStream input();

	/**
	 * Reports whether the actual workload is still alive.
	 *
	 * @return workload liveness
	 */
	boolean isAlive();

	/**
	 * Completes when the workload exits.
	 *
	 * @return exit notification
	 */
	@NotNull CompletableFuture<Void> onExit();

	/**
	 * Waits for the workload to exit within a bounded duration.
	 *
	 * @param timeout maximum wait
	 * @return whether the workload exited
	 * @throws InterruptedException when the caller is interrupted
	 */
	boolean await(@NotNull Duration timeout) throws InterruptedException;

	/**
	 * Terminates the actual workload, including its descendants or container.
	 *
	 * @param force whether to escalate immediately
	 */
	void terminate(boolean force);

	/**
	 * Suspends the workload, including its descendants or container, while it keeps its connections and memory.
	 *
	 * @throws UnsupportedOperationException when the execution cannot suspend its workload, with the reason
	 */
	default void freeze() {
		throw new UnsupportedOperationException("This execution provider cannot freeze a process");
	}

	/**
	 * Resumes a workload {@link #freeze() frozen} before; a workload that is not frozen is left as it is.
	 */
	default void thaw() {
	}

	/**
	 * Runs a command inside the workload, such as a tool its container image brings, and waits for it to finish.
	 *
	 * @param timeout how long to wait
	 * @param command the command and its arguments
	 * @return its exit code and output
	 * @throws UnsupportedOperationException when the execution cannot run a command inside its workload
	 * @throws IllegalStateException         when the command does not finish in time
	 */
	default @NotNull RunningContainer.CommandResult execute(@NotNull Duration timeout, @NotNull String... command) {
		throw new UnsupportedOperationException("This execution cannot run a command inside its process");
	}
}
