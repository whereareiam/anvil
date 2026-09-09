package me.whereareiam.anvil.environment.execution.api.process;

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
}
