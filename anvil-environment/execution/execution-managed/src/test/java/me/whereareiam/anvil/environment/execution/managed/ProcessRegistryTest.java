package me.whereareiam.anvil.environment.execution.managed;

import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.type.ProcessState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
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
			assertThrows(NoSuchElementException.class, () -> group.kill("serverr"));
		}

		assertEquals("finish:true", fixture.calls.getLast());
	}

	@Test
	void aKilledProcessStopsWithoutItsStopCommandOrFailingTheRunAndStartsAgainOnItsAddress() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		var group = fixture.service().start(fixture.plan(fixture.spec("proxy", true, "server"), fixture.spec("server", false)),
				fixture.preparation);
		try (group) {
			RunningProcess killed = group.get("server");
			group.kill("server");

			assertEquals(ProcessState.STOPPED, killed.state());
			assertEquals(List.of("server"), fixture.forcedTerminations);
			assertTrue(fixture.calls.indexOf("detach:server:1") < fixture.calls.indexOf("stop:server"),
					"the launch attachments are released before the process ends, as on a stop");
			assertEquals(ProcessState.READY, group.get("proxy").state());
			assertTrue(killed.console().tail(5).getLast().contains("was killed on request without a clean shutdown"));

			RunningProcess replacement = group.start("server");
			assertNotEquals(killed.executionId(), replacement.executionId());
			assertEquals(killed.address(), replacement.address());
			assertEquals(ProcessState.READY, replacement.state());
			group.kill("server");
			group.kill("server");
		}

		assertEquals("finish:true", fixture.calls.getLast());
	}
}
