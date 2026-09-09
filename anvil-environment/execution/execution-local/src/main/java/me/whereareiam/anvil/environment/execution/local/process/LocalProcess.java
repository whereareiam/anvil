package me.whereareiam.anvil.environment.execution.local.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Controls one host process tree and its standard streams.
 */
@RequiredArgsConstructor
public final class LocalProcess implements ProcessExecution {
	private final Process process;

	@Override
	public @NotNull InputStream output() {
		return process.getInputStream();
	}

	@Override
	public @NotNull OutputStream input() {
		return process.getOutputStream();
	}

	@Override
	public boolean isAlive() {
		return process.isAlive();
	}

	@Override
	public @NotNull CompletableFuture<Void> onExit() {
		return process.onExit().thenApply(ignored -> null);
	}

	@Override
	public boolean await(@NotNull Duration timeout) throws InterruptedException {
		return process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
	}

	@Override
	public void terminate(boolean force) {
		if (force) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
			return;
		}

		process.descendants().forEach(ProcessHandle::destroy);
		process.destroy();
	}
}
