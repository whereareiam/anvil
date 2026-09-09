package me.whereareiam.anvil.environment.execution.managed;

import me.whereareiam.anvil.api.type.ProcessState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProcessSlotTest {
	@TempDir
	Path directory;

	@Test
	void retainsPreparationAndEndpointsButReplacesCommandsAttachmentsAndRunningHandles() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		var group = fixture.service().start(fixture.plan(fixture.spec("server", false)), fixture.preparation);
		var original = group.server("server");
		var replacement = group.restart("server");
		assertNotSame(original, replacement);
		assertEquals(original.address(), replacement.address());
		assertEquals(ProcessState.STOPPED, original.state());
		assertEquals(ProcessState.READY, replacement.state());
		assertEquals(List.of("1", "2"), fixture.targets.get("server").commands.stream()
				.map(command -> command.getEnvironment().get("GENERATION")).toList());
		assertEquals(1, fixture.calls.stream().filter("prepare:server"::equals).count());
		assertTrue(fixture.calls.indexOf("detach:server:1") < fixture.calls.indexOf("stop:server"));
		assertTrue(fixture.calls.indexOf("stop:server") < fixture.calls.indexOf("configure:server:2"));
		group.finish(true);
		assertEquals(1, fixture.calls.stream().filter("detach:server:2"::equals).count());
		assertEquals(1, fixture.calls.stream().filter("prepared-finish:server:true"::equals).count());
	}

	@Test
	void failedRestartAttachmentStillStopsTheOldProcessAndPreventsSuccessfulFinalization() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		var group = fixture.service().start(fixture.plan(fixture.spec("server", false)), fixture.preparation);
		var original = group.server("server");
		fixture.attachmentCleanupFailure = new IllegalStateException("detach failed");
		assertSame(fixture.attachmentCleanupFailure, assertThrows(IllegalStateException.class, () -> group.restart("server")));
		assertEquals(ProcessState.STOPPED, original.state());
		assertFalse(fixture.calls.contains("configure:server:2"));
		fixture.attachmentCleanupFailure = null;
		group.finish(true);
		assertTrue(fixture.calls.contains("prepared-finish:server:false"));
		assertEquals("finish:false", fixture.calls.getLast());
	}
}
