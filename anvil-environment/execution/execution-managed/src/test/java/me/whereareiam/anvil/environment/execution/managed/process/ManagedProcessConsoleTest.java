package me.whereareiam.anvil.environment.execution.managed.process;

import me.whereareiam.anvil.api.model.process.console.ConsoleLine;
import me.whereareiam.anvil.api.model.process.console.ConsoleOutput;
import me.whereareiam.anvil.environment.execution.api.process.ProcessExecution;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class ManagedProcessConsoleTest {
	@TempDir Path directory;

	@Test
	void supportsIndependentCursorsWithoutDuplicatesAndReportsEvictedHistory() {
		ManagedProcessConsole console = new ManagedProcessConsole("server", directory);
		console.append("first");
		console.append("second");
		ConsoleOutput firstReader = console.read(0, 1);
		assertEquals(List.of("first"), firstReader.getLines().stream().map(ConsoleLine::getText).toList());
		assertEquals(1, firstReader.getNextCheckpoint());
		assertEquals(2, console.read(0, 10).getLines().size());
		assertEquals("second", console.read(firstReader.getNextCheckpoint(), 1).getLines().getFirst().getText());
		assertTrue(console.read(2, 1).getLines().isEmpty());
		for (int index = 0; index < 2100; index++) console.append("extra " + index);
		ConsoleOutput evicted = console.read(2, 1);
		assertTrue(evicted.isTruncated());
		assertEquals(103, evicted.getNextCheckpoint());
		assertFalse(console.read(evicted.getNextCheckpoint(), 1).isTruncated());
		assertThrows(IllegalArgumentException.class, () -> console.read(3000, 1));
		assertThrows(IllegalArgumentException.class, () -> console.read(0, 0));
	}

	@Test
	void awaitingTheOutputOfAnExitedProcessLeavesNothingToBeWrittenToItsWorkspaceLater() throws Exception {
		ManagedProcessConsole console = new ManagedProcessConsole("server", directory);
		PipedOutputStream output = new PipedOutputStream();
		PipedInputStream captured = new PipedInputStream(output);
		console.attach(new Exited(captured), Pattern.compile("READY"), () -> { }, () -> { });

		// The last line arrives after the process is gone, as it does when the capture thread is scheduled late.
		Thread late = new Thread(() -> {
			try {
				Thread.sleep(300);
				output.write("last words\n".getBytes(StandardCharsets.UTF_8));
				output.close();
			} catch (IOException | InterruptedException failure) {
				throw new IllegalStateException(failure);
			}
		});
		late.start();

		console.awaitOutput(Duration.ofSeconds(10));

		assertEquals(List.of("last words"), Files.readAllLines(directory.resolve("anvil-console.log")));
		late.join();
	}

	@Test
	void stopsAwaitingOutputThatASurvivingDescendantKeepsOpen() throws Exception {
		ManagedProcessConsole console = new ManagedProcessConsole("server", directory);
		PipedOutputStream held = new PipedOutputStream();
		console.attach(new Exited(new PipedInputStream(held)), Pattern.compile("READY"), () -> { }, () -> { });

		long started = System.nanoTime();
		console.awaitOutput(Duration.ofMillis(200));

		assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(5)) < 0);
		held.close();
	}

	/**
	 * A process that has exited and whose output is read from the given stream.
	 */
	private record Exited(InputStream output) implements ProcessExecution {
		@Override
		public @NotNull OutputStream input() { return OutputStream.nullOutputStream(); }

		@Override
		public boolean isAlive() { return false; }

		@Override
		public @NotNull CompletableFuture<Void> onExit() { return CompletableFuture.completedFuture(null); }

		@Override
		public boolean await(@NotNull Duration timeout) { return true; }

		@Override
		public void terminate(boolean force) { }
	}
}
