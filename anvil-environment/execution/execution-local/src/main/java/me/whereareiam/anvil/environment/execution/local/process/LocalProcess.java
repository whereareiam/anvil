package me.whereareiam.anvil.environment.execution.local.process;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Controls one host process tree and its standard streams.
 */
@RequiredArgsConstructor
public final class LocalProcess implements ProcessExecution {
	private static final Duration SIGNAL_TIMEOUT = Duration.ofSeconds(10);

	private final Process process;
	private volatile boolean frozen;

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
		// A stopped process would take its signal only once it runs again.
		thaw();
		if (force) {
			process.descendants().forEach(ProcessHandle::destroyForcibly);
			process.destroyForcibly();
			return;
		}

		process.descendants().forEach(ProcessHandle::destroy);
		process.destroy();
	}

	/**
	 * Stops the process and its children with {@code SIGSTOP}, which they cannot catch, so they keep their sockets but
	 * run nothing. Needs the {@code kill} command of a POSIX system.
	 */
	@Override
	public synchronized void freeze() {
		if (frozen) return;

		signal("STOP");
		frozen = true;
	}

	@Override
	public synchronized void thaw() {
		if (!frozen) return;

		signal("CONT");
		frozen = false;
	}

	private void signal(String signal) {
		if (System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win"))
			throw new UnsupportedOperationException("Local execution freezes a process with POSIX signals, which Windows lacks");

		List<String> command = new ArrayList<>(List.of("kill", "-" + signal, Long.toString(process.pid())));
		process.descendants().forEach(child -> command.add(Long.toString(child.pid())));
		try {
			Process kill = new ProcessBuilder(command).redirectErrorStream(true).start();
			if (!kill.waitFor(SIGNAL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
				kill.destroyForcibly();
				throw new IllegalStateException("Could not send SIG" + signal + " within " + SIGNAL_TIMEOUT);
			}
			if (kill.exitValue() != 0 && process.isAlive())
				throw new IllegalStateException("kill -" + signal + " failed: " + new String(kill.getInputStream().readAllBytes()).trim());
		} catch (IOException failure) {
			throw new IllegalStateException("Could not run kill -" + signal, failure);
		} catch (InterruptedException failure) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted sending SIG" + signal, failure);
		}
	}
}
