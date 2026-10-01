package me.whereareiam.anvil.integration.intellij.tooling.process;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.Getter;
import lombok.Setter;

/**
 * Local protocol fixture exercising the real IDE launch writer without running a JVM or Minecraft.
 */
public final class ControlledToolingProcess extends Process {
	private static final ObjectMapper JSON = new ObjectMapper();
	private final PipedInputStream output = new PipedInputStream(64 * 1024);
	private final PrintWriter emitted;
	private final CountDownLatch terminated = new CountDownLatch(1);
	@Getter private final AtomicInteger starts = new AtomicInteger();
	private final List<JsonNode> requests = new CopyOnWriteArrayList<>();
	private final boolean runner;
	@Setter private volatile boolean holdCleanup;
	@Getter private volatile boolean closeRequested;
	@Setter private volatile boolean failCommandFlush;

	@Setter
	private String scenariosJson =
			"[{\"definition\":\"fixture.Definition\",\"name\":\"example\",\"displayName\":\"Example\",\"entrypoint\":\"server\",\"processes\":[]}]";

	private volatile CountDownLatch heldFlush = new CountDownLatch(0);
	private volatile CountDownLatch flushEntered = new CountDownLatch(1);
	private volatile int exitCode;

	public ControlledToolingProcess(boolean runner) {
		this.runner = runner;
		try {
			emitted = new PrintWriter(new PipedOutputStream(output), true, StandardCharsets.UTF_8);
		} catch (IOException failure) {
			throw new AssertionError(failure);
		}
	}

	public List<JsonNode> getRequests() {
		return List.copyOf(requests);
	}

	public void holdCommandFlush() {
		heldFlush = new CountDownLatch(1);
		flushEntered = new CountDownLatch(1);
	}

	public boolean isCommandFlushWaiting() {
		return flushEntered.getCount() == 0 && heldFlush.getCount() != 0;
	}

	public void releaseCommandFlush() {
		heldFlush.countDown();
	}

	public void emit(String line) {
		emitted.println(line);
	}

	public void complete(int code) {
		exitCode = code;
		releaseCommandFlush();
		emitted.close();
		terminated.countDown();
	}

	@Override
	public OutputStream getOutputStream() {
		return new OutputStream() {
			private final ByteArrayOutputStream line = new ByteArrayOutputStream();
			private boolean command;

			@Override
			public void write(int value) throws IOException {
				if (value != '\n') {
					line.write(value);
					return;
				}
				JsonNode request = JSON.readTree(line.toByteArray());
				line.reset();
				requests.add(request);
				String operation = request.path("operation").asText();
				command = List.of("console", "action").contains(operation);
				if (operation.equals("scenarios"))
					emit(
							"{\"type\":\"response\",\"id\":\""
									+ request.path("id").asText()
									+ "\",\"success\":true,\"result\":"
									+ scenariosJson
									+ "}");
				else if (operation.equals("start")) {
					starts.incrementAndGet();
					var snapshot =
							JSON.createObjectNode()
									.put("state", "RUNNING")
									.put("definition", request.path("definition").asText())
									.put("scenario", request.path("scenario").asText());
					snapshot.putArray("processes");
					emit(
							JSON.createObjectNode().put("type", "snapshot").set("snapshot", snapshot).toString());
				}
			}

			@Override
			public void flush() throws IOException {
				if (!command) return;
				flushEntered.countDown();
				try {
					heldFlush.await();
				} catch (InterruptedException failure) {
					Thread.currentThread().interrupt();
					throw new IOException(failure);
				}
				command = false;
				if (failCommandFlush) throw new IOException("Controlled command write failure");
			}

			@Override
			public void close() {
				closeRequested = true;
				if (holdCleanup) return;
				if (runner) emit("{\"type\":\"snapshot\",\"snapshot\":{\"state\":\"STOPPED\"}}");
				complete(0);
			}
		};
	}

	@Override
	public InputStream getInputStream() {
		return output;
	}

	@Override
	public InputStream getErrorStream() {
		return new ByteArrayInputStream(new byte[0]);
	}

	@Override
	public int waitFor() throws InterruptedException {
		terminated.await();
		return exitCode;
	}

	@Override
	public int exitValue() {
		if (isAlive()) throw new IllegalThreadStateException("Still running");
		return exitCode;
	}

	@Override
	public boolean isAlive() {
		return terminated.getCount() != 0;
	}

	@Override
	public void destroy() {
		complete(143);
	}
}
