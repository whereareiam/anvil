package me.whereareiam.anvil.environment.execution.local.process;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProcessTest {
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
