package me.whereareiam.anvil.environment.execution.managed.process;

import me.whereareiam.anvil.api.model.process.console.ConsoleLine;
import me.whereareiam.anvil.api.model.process.console.ConsoleOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

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
}
