package me.whereareiam.anvil.environment.execution.managed.process;

import me.whereareiam.anvil.api.exception.ProcessException;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.environment.execution.local.process.LocalProcess;
import me.whereareiam.anvil.environment.execution.managed.process.type.ManagedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedProcessTest {
	@TempDir
	Path temporary;

	@Test
	void supervisesAndStopsAReadyProcess() {
		ManagedServer process = process("ready");
		start(process,
				List.of("sh", "-c", "echo READY; while read line; do [ \"$line\" = stop ] && exit 0; done"),
				Map.of(),
				Pattern.compile("READY"),
				Duration.ofSeconds(3)
		);

		assertEquals(ProcessState.READY, process.state());
		process.stop(Duration.ofSeconds(3));
		assertEquals(ProcessState.STOPPED, process.state());
		assertTrue(process.console().tail(10).contains("READY"));
	}

	@Test
	void reportsReadinessTimeoutAndAllowsOwnerCleanup() {
		ManagedServer process = process("timeout");
		try {
			ProcessException failure = assertThrows(ProcessException.class, () -> start(process,
					List.of("sh", "-c", "while read line; do [ \"$line\" = stop ] && exit 0; done"),
					Map.of(), Pattern.compile("READY"), Duration.ofMillis(100)
			));
			assertEquals("timeout", failure.getProcessName());
			assertEquals(ProcessState.FAILED, process.state());
		} finally {
			process.stop(Duration.ofSeconds(1));
		}
		assertEquals(ProcessState.STOPPED, process.state());
	}

	@Test
	void marksProcessFailedWhenExecutionCannotBeCreated() {
		ManagedServer process = process("launch-failure");
		assertThrows(UncheckedIOException.class, () -> process.start(
				() -> { throw new UncheckedIOException(new IOException("launch failed")); },
				Pattern.compile("READY"),
				"stop",
				Duration.ofSeconds(1)
		));
		assertEquals(ProcessState.FAILED, process.state());
	}

	@Test
	void reportsExitBeforeReadinessWithoutWaitingForTheDeadline() {
		ManagedServer process = process("early-exit");
		try {
			assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThrows(ProcessException.class, () -> start(process,
					List.of("sh", "-c", "echo FAILED; exit 7"), Map.of(), Pattern.compile("READY"), Duration.ofSeconds(30)
			)));
			assertEquals(ProcessState.FAILED, process.state());
		} finally {
			process.stop(Duration.ofSeconds(1));
		}
	}

	@Test
	void reportsCaptureFailureWithoutWaitingForReadinessTimeout() throws Exception {
		ManagedServer process = process("capture-failure");
		Files.createDirectories(process.workDirectory().resolve("anvil-console.log"));
		try {
			assertTimeoutPreemptively(Duration.ofSeconds(5), () -> assertThrows(ProcessException.class, () -> start(process,
					List.of("sh", "-c", "exec sleep 30"), Map.of(), Pattern.compile("READY"), Duration.ofSeconds(30)
			)));
			assertTrue(process.console().tail(10).stream().anyMatch(line -> line.contains("Failed to capture process output")));
		} finally {
			process.stop(Duration.ofMillis(100));
		}
	}

	@Test
	void escalatesWhenTheProcessIgnoresStopAndTermination() {
		ManagedServer process = process("stubborn");
		start(process,
				List.of("sh", "-c", "trap '' TERM; echo READY; while read line; do :; done"),
				Map.of(), Pattern.compile("READY"), Duration.ofSeconds(3)
		);
		try {
			assertTimeoutPreemptively(Duration.ofSeconds(5), () -> process.stop(Duration.ofMillis(50)));
			assertEquals(ProcessState.STOPPED, process.state());
			assertThrows(ProcessException.class, () -> process.console().sendCommand("after-stop"));
			process.stop(Duration.ofMillis(50));
		} finally {
			process.stop(Duration.ofMillis(50));
		}
	}

	@Test
	void boundsHistoryWhilePersistingAllOutputAndKeepsSnapshotsImmutable() throws Exception {
		ManagedServer process = process("history");
		start(process,
				List.of("sh", "-c", "i=0; while [ $i -lt 2100 ]; do echo line-$i; i=$((i + 1)); done; echo READY; while read line; do [ \"$line\" = stop ] && exit 0; done"),
				Map.of(), Pattern.compile("READY"), Duration.ofSeconds(3)
		);
		try {
			var snapshot = process.console().tail(3000);
			assertEquals(2000, snapshot.size());
			assertFalse(snapshot.contains("line-0"));
			assertTrue(Files.readString(process.workDirectory().resolve("anvil-console.log")).contains("line-0"));
			assertThrows(UnsupportedOperationException.class, snapshot::clear);
			assertTrue(process.console().tail(0).isEmpty());
			assertThrows(IllegalArgumentException.class, () -> process.console().tail(-1));
			assertThrows(IllegalStateException.class, () -> start(process,
					List.of("sh"), Map.of(), Pattern.compile("READY"), Duration.ofSeconds(1)));
		} finally {
			process.stop(Duration.ofSeconds(1));
		}
	}

	private void start(
			ManagedServer process, List<String> command, Map<String, String> environment, Pattern readiness, Duration timeout
	) {
		process.start(() -> {
			try {
				Files.createDirectories(process.workDirectory());
				ProcessBuilder builder = new ProcessBuilder(command)
						.directory(process.workDirectory().toFile())
						.redirectErrorStream(true);

				builder.environment().putAll(environment);

				return new LocalProcess(builder.start());
			} catch (IOException failure) {
				throw new UncheckedIOException(failure);
			}
		}, readiness, "stop", timeout);
	}

	private ManagedServer process(String name) {
		return new ManagedServer(name, new InetSocketAddress("127.0.0.1", 25565), temporary.resolve(name));
	}

	@Test
	void terminatesTheProcessEvenWhenItsConsoleIsClosed() {
		ManagedServer process = process("closed-console");
		start(process,
				List.of("sh", "-c", "exec 0<&-; echo READY; exec sleep 60"),
				Map.of(), Pattern.compile("READY"), Duration.ofSeconds(3)
		);

		try {
			assertThrows(ProcessException.class,
					() -> process.stop(Duration.ofMillis(100)));
			assertEquals(ProcessState.STOPPED, process.state());
		} finally {
			process.stop(Duration.ofMillis(100));
		}
	}

	@Test
	void consoleCheckpointExcludesOldLinesAndWaitsForNewOutput() {
		ManagedServer process = echoProcess("cursor");

		try {
			assertEquals("READY", process.console().await("READY", 0, Duration.ofSeconds(1)));
			long checkpoint = process.console().checkpoint();
			assertThrows(ProcessException.class, () -> process.console().await("READY", checkpoint, Duration.ofMillis(50)));
			process.console().sendCommand("READY");
			assertEquals("READY", process.console().await("READY", checkpoint, Duration.ofSeconds(1)));
			assertTrue(process.console().checkpoint() > checkpoint);
			assertThrows(IllegalArgumentException.class,
						() -> process.console().await("x", process.console().checkpoint() + 1, Duration.ofSeconds(1)));
		} finally {
			process.stop(Duration.ofSeconds(1));
		}
	}

	@Test
	void consoleWaitReportsClosedOutputAndPreservesInterruption() {
		ManagedServer process = echoProcess("closed-output");
		try {
			Thread.currentThread().interrupt();
			assertThrows(
				ProcessException.class,
				() -> process.console().await("missing", process.console().checkpoint(), Duration.ofSeconds(1)));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
			process.stop(Duration.ofSeconds(1));
		}

		assertTrue(assertThrows(
					ProcessException.class,
					() -> process.console().await("missing", process.console().checkpoint(), Duration.ofSeconds(10)))
					.getMessage()
					.contains("no further console output"));
	}

	@Test
	void consoleWaitRejectsEvictedHistory() {
		ManagedServer process = process("overflow");
		start(process, List.of("sh", "-c",
							"echo READY; read line; i=0; while [ $i -lt 2500 ]; do echo LINE; i=$((i+1)); done; " +
							"echo END; read line"),
					Map.of(), Pattern.compile("READY"), Duration.ofSeconds(3));

		try {
			process.console().sendCommand("flood");
			assertTrue(
				assertThrows(ProcessException.class, () -> process.console().await("END", 0, Duration.ofSeconds(3)))
					.getMessage()
					.contains("evicted"));
		} finally {
			process.stop(Duration.ofSeconds(1));
		}
	}

	private ManagedServer echoProcess(String name) {
		ManagedServer process = process(name);
		start(
				process,
				List.of("sh", "-c", "echo READY; while read line; do [ \"$line\" = stop ] && exit 0; echo \"$line\"; done"),
				Map.of(), Pattern.compile("READY"), Duration.ofSeconds(3)
		);

		return process;
	}
}
