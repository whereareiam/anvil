package me.whereareiam.anvil.environment.execution.local.process;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProcessTest {
	@Test
	@DisabledOnOs(OS.WINDOWS)
	void aFrozenProcessTreeRunsNothingUntilThawedAndAGracefulStopThawsIt() throws Exception {
		Process shell = new ProcessBuilder("sh", "-c", "while true; do echo tick; sleep 0.05; done")
				.redirectErrorStream(true)
				.start();
		LocalProcess process = new LocalProcess(shell);
		AtomicInteger ticks = new AtomicInteger();
		Thread reader = Thread.ofVirtual().start(() -> {
			try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.output(), StandardCharsets.UTF_8))) {
				while (lines.readLine() != null) ticks.incrementAndGet();
			} catch (IOException ignored) {
				// The stream closes with the process.
			}
		});
		try {
			awaitTicks(ticks, 3);

			process.freeze();
			Thread.sleep(300);
			int frozen = ticks.get();
			Thread.sleep(500);
			assertEquals(frozen, ticks.get(), "a frozen process printed");

			process.thaw();
			awaitTicks(ticks, frozen + 3);

			process.freeze();
			process.terminate(false);
			assertTrue(process.await(Duration.ofSeconds(5)), "a graceful stop reaches a frozen process");
		} finally {
			shell.descendants().forEach(ProcessHandle::destroyForcibly);
			shell.destroyForcibly();
			reader.join(5000);
		}
	}

	private static void awaitTicks(AtomicInteger ticks, int expected) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (ticks.get() < expected) {
			assertTrue(System.nanoTime() < deadline, "the process stopped printing");
			Thread.sleep(20);
		}
	}

	@Test
	void forcedTerminationKillsAProcessTreeThatIgnoresAGracefulStop() throws Exception {
		Process shell = new ProcessBuilder("sh", "-c", "trap '' TERM; sleep 60 & echo $!; wait")
				.redirectErrorStream(true)
				.start();
		LocalProcess process = new LocalProcess(shell);
		try {
			long child = Long.parseLong(new BufferedReader(new InputStreamReader(process.output(), StandardCharsets.UTF_8)).readLine());
			ProcessHandle descendant = ProcessHandle.of(child).orElseThrow();

			process.terminate(false);
			assertFalse(process.await(Duration.ofMillis(300)), "the shell ignores a graceful stop");

			process.terminate(true);

			assertTrue(process.await(Duration.ofSeconds(5)));
			descendant.onExit().get(5, TimeUnit.SECONDS);
			assertFalse(descendant.isAlive());
		} finally {
			shell.descendants().forEach(ProcessHandle::destroyForcibly);
			shell.destroyForcibly();
		}
	}
}
