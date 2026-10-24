package me.whereareiam.anvil.workbench.remote;

import com.fasterxml.jackson.databind.JsonNode;
import me.whereareiam.anvil.workbench.protocol.WireProtocol;
import org.jetbrains.annotations.NotNull;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

/**
 * A JVM a workbench on another machine started outside managed execution, such as a player worker. Its standard
 * output and input share one stream connection and its standard error has its own.
 */
final class RemoteProcess extends Process {
	private static final long WAIT_SLICE_MILLIS = TimeUnit.MINUTES.toMillis(5);

	private final @NotNull WorkbenchConnection connection;
	private final @NotNull String handle;
	private final @NotNull SSLSocket stdio;
	private final @NotNull SSLSocket stderr;
	private volatile Integer exitCode;

	RemoteProcess(@NotNull WorkbenchConnection connection, @NotNull String handle) throws IOException {
		this.connection = connection;
		this.handle = handle;
		this.stdio = connection.stream(WireProtocol.STDIO, connection.mapper().createObjectNode().put("handle", handle));
		try {
			this.stderr = connection.stream(WireProtocol.STDERR, connection.mapper().createObjectNode().put("handle", handle));
		} catch (IOException failure) {
			stdio.close();
			throw failure;
		}
	}

	@Override
	public OutputStream getOutputStream() {
		try {
			return stdio.getOutputStream();
		} catch (IOException failure) {
			return OutputStream.nullOutputStream();
		}
	}

	@Override
	public InputStream getInputStream() {
		try {
			return stdio.getInputStream();
		} catch (IOException failure) {
			return InputStream.nullInputStream();
		}
	}

	@Override
	public InputStream getErrorStream() {
		try {
			return stderr.getInputStream();
		} catch (IOException failure) {
			return InputStream.nullInputStream();
		}
	}

	@Override
	public int waitFor() throws InterruptedException {
		while (!waitFor(WAIT_SLICE_MILLIS, TimeUnit.MILLISECONDS)) {
			// Each slice is one request, so no answer waits long enough to time out on the way.
		}
		return exitCode;
	}

	@Override
	public boolean waitFor(long timeout, @NotNull TimeUnit unit) throws InterruptedException {
		if (exitCode != null) return true;
		if (Thread.interrupted()) throw new InterruptedException();

		JsonNode code = connection.call("launched.waitFor", connection.mapper().createObjectNode()
				.put("handle", handle).put("timeoutMillis", unit.toMillis(timeout)));
		if (code.isNull()) return false;

		exitCode = code.asInt();
		return true;
	}

	@Override
	public int exitValue() {
		try {
			if (!waitFor(0, TimeUnit.MILLISECONDS)) throw new IllegalThreadStateException("The process on the workbench has not exited");
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			throw new IllegalThreadStateException("Interrupted while asking the workbench");
		}
		return exitCode;
	}

	@Override
	public boolean isAlive() {
		try {
			return !waitFor(0, TimeUnit.MILLISECONDS);
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
			return true;
		}
	}

	@Override
	public void destroy() {
		destroy(false);
	}

	@Override
	public Process destroyForcibly() {
		destroy(true);
		return this;
	}

	private void destroy(boolean force) {
		try {
			connection.call("launched.destroy", connection.mapper().createObjectNode().put("handle", handle).put("force", force));
		} catch (RuntimeException gone) {
			// The workbench or the process is gone already.
		}
	}
}
