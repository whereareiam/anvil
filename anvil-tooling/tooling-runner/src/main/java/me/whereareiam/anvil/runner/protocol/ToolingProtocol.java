package me.whereareiam.anvil.runner.protocol;

import me.whereareiam.anvil.tooling.api.ToolingSession;
import me.whereareiam.anvil.tooling.api.model.LogEvent;
import me.whereareiam.anvil.tooling.api.model.SessionSnapshot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Versioned JSON-lines protocol. A serialized command worker owns mutations while snapshots
 * and output remain observable. Closing stdin or the JVM interrupts startup and closes the session.
 */
public final class ToolingProtocol {
	private final ToolingSession session;
	private final BufferedReader input;
	private final ToolingProtocolWriter writer;
	private final ToolingRequestReader reader = new ToolingRequestReader();
	private final ToolingOperationRegistry operations;
	private final ExecutorService commands = Executors.newSingleThreadExecutor();
	private final ScheduledExecutorService events = Executors.newSingleThreadScheduledExecutor();
	private final Object dispatch = new Object();
	private final AtomicBoolean closed = new AtomicBoolean();
	private long cancellation;
	private @Nullable Thread executing;
	private @Nullable ToolingOperationRegistry.Binding<?, ?> activeOperation;
	private @Nullable SessionSnapshot lastSnapshot;

	/**
	 * Binds a session to a structured request/response stream.
	 * The protocol closes the session when serving ends; stream ownership remains with the caller.
	 *
	 * @param session session whose lifecycle is owned by this protocol run
	 * @param input line-delimited request input
	 * @param output protocol responses and events; must not contain application diagnostics
	 */
	public ToolingProtocol(
			@NotNull ToolingSession session,
			@NotNull Reader input,
			@NotNull PrintWriter output
	) {
		this.session = session;
		this.input = new BufferedReader(input);
		this.writer = new ToolingProtocolWriter(output);
		this.operations = new ToolingOperationRegistry(session);
	}

	/**
	 * Serves requests until input ends, then settles work and releases the owned session.
	 * A protocol instance serves one connection and cannot be reused after completion.
	 *
	 * @throws IOException when the input cannot be read
	 */
	public void run() throws IOException {
		Thread shutdown = new Thread(this::shutdown, "anvil-tooling-shutdown");
		Runtime.getRuntime().addShutdownHook(shutdown);
		try {
			writer.ready();
			events.scheduleWithFixedDelay(this::observe, 0, 250, TimeUnit.MILLISECONDS);
			String line;
			while ((line = input.readLine()) != null) {
				ToolingRequestReader.Request request;
				try {
					request = reader.read(line);
				} catch (RuntimeException | IOException failure) {
					// A malformed request has no trustworthy correlation ID. Report it as a protocol
					// error instead of manufacturing an invalid response with an empty ID.
					writer.error(failure);
					continue;
				}
				var operation = operations.find(request.operation());
				long generation;
				synchronized (dispatch) {
					if (operation != null && operation.cancelsPending()) {
						cancellation++;
						if (executing != null && activeOperation != null && activeOperation.cancellable())
							executing.interrupt();
					}
					generation = cancellation;
				}
				commands.execute(() -> execute(request, operation, generation));
				writer.queued(request.id());
			}
		} finally {
			shutdown();
			try {
				Runtime.getRuntime().removeShutdownHook(shutdown);
			} catch (IllegalStateException ignored) {
				// The JVM is already executing the registered cleanup hook.
			}
		}
	}

	private void execute(
			ToolingRequestReader.Request request,
			@Nullable ToolingOperationRegistry.Binding<?, ?> operation,
			long generation
	) {
		String id = request.id();
		try {
			if (operation == null) throw new IllegalArgumentException("Unknown tooling operation: " + request.operation());
			synchronized (dispatch) {
				if (operation.cancellable() && generation != cancellation)
					throw new CancellationException("Request was cancelled before execution");
				executing = Thread.currentThread();
				activeOperation = operation;
			}
			Object result = operation.execute(request, reader);
			writer.success(id, result);
		} catch (IOException | RuntimeException | Error failure) {
			writer.failure(id, failure);
		} finally {
			synchronized (dispatch) {
				executing = null;
				activeOperation = null;
			}
		}
	}

	private synchronized void observe() {
		try {
			SessionSnapshot snapshot = session.snapshot();
			if (!snapshot.equals(lastSnapshot)) {
				writer.snapshot(snapshot);
				lastSnapshot = snapshot;
			}
			for (LogEvent event : session.drainLogs()) writer.log(event);
		} catch (RuntimeException failure) {
			// One failed poll is recoverable: the next poll publishes a fresh snapshot. An error frame would
			// make the client close the whole connection, so report it as a diagnostic instead.
			System.err.println("Could not publish an Anvil session snapshot: " + failure);
		} catch (Error failure) {
			writer.error(failure);
		}
	}

	private void shutdown() {
		if (!closed.compareAndSet(false, true)) return;
		commands.shutdownNow();
		events.shutdownNow();
		try {
			session.close();
		} finally {
			observe();
		}
	}

}
