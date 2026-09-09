package me.whereareiam.anvil.environment.execution.docker.container;

import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import lombok.RequiredArgsConstructor;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns streams and exit notification for one process attachment.
 */
@RequiredArgsConstructor
public final class ContainerAttachment implements AutoCloseable {
	private final PipedInputStream output;
	private final PipedOutputStream outputWriter;
	private final PipedInputStream inputReader;
	private final PipedOutputStream input;
	private final ResultCallback.Adapter<Frame> callback;

	private final CompletableFuture<Void> exited = new CompletableFuture<>();
	private final AtomicBoolean closed = new AtomicBoolean();


	public InputStream output() {
		return output;
	}

	public OutputStream input() {
		return input;
	}

	public CompletableFuture<Void> exited() {
		return exited;
	}

	@Override
	public void close() {
		if (!closed.compareAndSet(false, true)) return;
		closeQuietly(callback);
		closeQuietly(outputWriter);
		closeQuietly(inputReader);
		closeQuietly(input);
	}

	private static void closeQuietly(Closeable resource) {
		try {
			resource.close();
		} catch (IOException ignored) {
		}
	}
}
