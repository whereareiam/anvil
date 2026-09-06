package me.whereareiam.anvil.engine.runtime.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import me.whereareiam.anvil.api.type.ProcessState;
import me.whereareiam.anvil.engine.AnvilException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManagedProcessTest {
	@TempDir Path temporary;

	@Test
	void supervisesAndStopsAReadyProcess() {
		ManagedServer process = process("ready");
		process.start(List.of("sh", "-c", "echo READY; while read line; do [ \"$line\" = stop ] && exit 0; done"),
					Map.of(), Pattern.compile("READY"), "stop", Duration.ofSeconds(3));

		assertEquals(ProcessState.READY, process.state());
		process.stop(Duration.ofSeconds(3));
		assertEquals(ProcessState.STOPPED, process.state());
		assertTrue(process.console().tail(10).contains("READY"));
	}

	private ManagedServer process(String name) {
		return new ManagedServer(name, new InetSocketAddress("127.0.0.1", 25565), temporary.resolve(name));
	}

	@Test
	void terminatesTheProcessEvenWhenItsConsoleIsClosed() {
		ManagedServer process = process("closed-console");
		process.start(List.of("sh", "-c", "exec 0<&-; echo READY; exec sleep 60"), Map.of(), Pattern.compile("READY"),
					"stop", Duration.ofSeconds(3));
		try {
			assertThrows(AnvilException.class, () -> process.stop(Duration.ofMillis(100)));
			assertEquals(ProcessState.STOPPED, process.state());
		} finally {
			process.stop(Duration.ofMillis(100));
		}
	}
	@Test
	void consoleCursorExcludesOldLinesAndWaitsForNewOutput() {
		ManagedServer process = echoProcess("cursor");
		try {
			assertEquals("READY", process.console().await("READY", 0, Duration.ofSeconds(1)));
			long checkpoint = process.console().checkpoint();
			assertThrows(AnvilException.class, () -> process.console().await("READY", checkpoint, Duration.ofMillis(50)));
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
				AnvilException.class,
				() -> process.console().await("missing", process.console().checkpoint(), Duration.ofSeconds(1)));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
			process.stop(Duration.ofSeconds(1));
		}
		assertTrue(assertThrows(
					AnvilException.class,
					() -> process.console().await("missing", process.console().checkpoint(), Duration.ofSeconds(10)))
					.getMessage()
					.contains("no further console output"));
	}

	@Test
	void consoleWaitRejectsEvictedHistory() {
		ManagedServer process = process("overflow");
		process.start(List.of("sh", "-c",
							"echo READY; read line; i=0; while [ $i -lt 2500 ]; do echo LINE; i=$((i+1)); done; " +
							"echo END; read line"),
					Map.of(), Pattern.compile("READY"), "stop", Duration.ofSeconds(3));
		try {
			process.console().sendCommand("flood");
			assertTrue(
				assertThrows(AnvilException.class, () -> process.console().await("END", 0, Duration.ofSeconds(3)))
					.getMessage()
					.contains("evicted"));
		} finally {
			process.stop(Duration.ofSeconds(1));
		}
	}

	private ManagedServer echoProcess(String name) {
		ManagedServer process = process(name);
		process.start(
			List.of("sh", "-c", "echo READY; while read line; do [ \"$line\" = stop ] && exit 0; echo \"$line\"; done"),
			Map.of(), Pattern.compile("READY"), "stop", Duration.ofSeconds(3));
		return process;
	}
}
