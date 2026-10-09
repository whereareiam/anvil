package me.whereareiam.anvil.environment.execution.managed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class ProcessRegistryTest {
	@TempDir
	Path directory;

	@Test
	void unknownProcessNameIsACallerErrorAndDoesNotFailTheRun() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		var group = fixture.service().start(fixture.plan(fixture.spec("server", false)), fixture.preparation);
		try (group) {
			assertThrows(NoSuchElementException.class, () -> group.start("serverr"));
			assertThrows(NoSuchElementException.class, () -> group.restart("serverr"));
			assertThrows(NoSuchElementException.class, () -> group.stop("serverr"));
		}

		assertEquals("finish:true", fixture.calls.getLast());
	}
}
