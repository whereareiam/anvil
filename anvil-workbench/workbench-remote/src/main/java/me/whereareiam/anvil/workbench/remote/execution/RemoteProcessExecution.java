package me.whereareiam.anvil.workbench.remote.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import me.whereareiam.anvil.api.process.type.RunningContainer;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import me.whereareiam.anvil.workbench.api.exception.WorkbenchException;
import me.whereareiam.anvil.workbench.protocol.WireProtocol;
import me.whereareiam.anvil.workbench.remote.WorkbenchConnection;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * A process running on a workbench on another machine. Its output and input travel over a stream connection of
 * their own; whether and when it ends the engine asks the workbench.
 */
final class RemoteProcessExecution implements ProcessExecution {
	private static final Duration EXIT_POLL = Duration.ofMinutes(5);

	private final @NotNull WorkbenchConnection connection;
	private final @NotNull String handle;
	private final @NotNull SSLSocket stdio;
	private final @NotNull CompletableFuture<Void> exit = new CompletableFuture<>();

	private RemoteProcessExecution(@NotNull WorkbenchConnection connection, @NotNull String handle, @NotNull SSLSocket stdio) {
		this.connection = connection;
		this.handle = handle;
		this.stdio = stdio;
		Thread.ofVirtual().name("anvil-remote-exit").start(this::watchExit);
	}

	/**
	 * Attaches to a process the workbench started.
	 *
	 * @param connection connection to the workbench
	 * @param handle the process's handle
	 * @return the attached execution
	 */
	static @NotNull RemoteProcessExecution attach(@NotNull WorkbenchConnection connection, @NotNull String handle) {
		try {
			return new RemoteProcessExecution(connection, handle,
					connection.stream(WireProtocol.STDIO, connection.mapper().createObjectNode().put("handle", handle)));
		} catch (IOException failure) {
			connection.call("process.terminate", connection.mapper().createObjectNode().put("handle", handle).put("force", true));
			throw new WorkbenchException("Could not attach to a process on workbench '" + connection.pairing().getName() + "'", failure);
		}
	}

	private void watchExit() {
		try {
			while (!await(EXIT_POLL)) {
				// A long wait per request keeps the workbench's answer from timing out anywhere on the way.
			}
			exit.complete(null);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		} catch (RuntimeException lost) {
			exit.completeExceptionally(lost);
		}
	}

	@Override
	public @NotNull InputStream output() {
		try {
			return stdio.getInputStream();
		} catch (IOException failure) {
			throw new WorkbenchException("The output of a process on the workbench is unavailable", failure);
		}
	}

	@Override
	public @NotNull OutputStream input() {
		try {
			return stdio.getOutputStream();
		} catch (IOException failure) {
			throw new WorkbenchException("The input of a process on the workbench is unavailable", failure);
		}
	}

	@Override
	public boolean isAlive() {
		return !exit.isDone() && connection.call("process.alive", arguments()).asBoolean();
	}

	@Override
	public @NotNull CompletableFuture<Void> onExit() {
		return exit;
	}

	@Override
	public boolean await(@NotNull Duration timeout) throws InterruptedException {
		if (exit.isDone()) return true;
		if (Thread.interrupted()) throw new InterruptedException();

		return connection.call("process.await", arguments().put("timeoutMillis", timeout.toMillis())).asBoolean();
	}

	@Override
	public void terminate(boolean force) {
		connection.call("process.terminate", arguments().put("force", force));
	}

	@Override
	public void freeze() {
		connection.call("process.freeze", arguments());
	}

	@Override
	public void thaw() {
		connection.call("process.thaw", arguments());
	}

	@Override
	public @NotNull RunningContainer.CommandResult execute(@NotNull Duration timeout, @NotNull String... command) {
		var arguments = arguments().put("timeoutMillis", timeout.toMillis());
		var parts = arguments.putArray("command");
		for (String part : command) parts.add(part);
		JsonNode result = connection.call("process.execute", arguments);

		return new RunningContainer.CommandResult(result.path("exitCode").asInt(), result.path("output").asText());
	}

	private ObjectNode arguments() {
		return connection.mapper().createObjectNode().put("handle", handle);
	}
}
