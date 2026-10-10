package me.whereareiam.anvil.environment.execution.managed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ManagedProcessServiceTest {
	@TempDir
	Path directory;

	@Test
	void allocatesCompleteTopologyBeforePreparationAndStartsDependenciesBeforeDependents() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		var plan = fixture.plan(fixture.spec("proxy", true, "server"), fixture.spec("server", false));
		var group = fixture.service().start(plan, fixture.preparation);
		assertEquals(Set.of("server", "proxy"), fixture.preparation.observedPeers.get("proxy").keySet());
		assertTrue(fixture.calls.indexOf("target:server") < fixture.calls.indexOf("prepare:proxy"));
		assertTrue(fixture.calls.indexOf("prepare:server") < fixture.calls.indexOf("configure:proxy:1"));
		assertTrue(fixture.calls.indexOf("configure:server:1") < fixture.calls.indexOf("start:server"));
		assertTrue(fixture.calls.indexOf("attach:server:1") < fixture.calls.indexOf("configure:proxy:1"));
		group.finish(true);
		group.finish(true);
		assertTrue(fixture.calls.indexOf("stop:proxy") < fixture.calls.indexOf("stop:server"));
		assertEquals(List.of("target-close:proxy", "target-close:server", "session-close",
				"prepared-finish:proxy:true", "prepared-finish:server:true", "finish:true"),
				fixture.calls.subList(fixture.calls.indexOf("target-close:proxy"), fixture.calls.size()));
	}

	@Test
	void suppliesRunningPeersToPreparationWithoutStartingOrStoppingThem() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		InetSocketAddress lobby = new InetSocketAddress("127.0.0.1", 25001);
		var plan = fixture.plan(fixture.spec("proxy", true)).toBuilder().peer("lobby", lobby).build();

		var group = fixture.service().start(plan, fixture.preparation);

		assertEquals(Set.of("lobby", "proxy"), fixture.preparation.observedPeers.get("proxy").keySet());
		assertEquals(lobby, fixture.preparation.observedPeers.get("proxy").get("lobby"));
		group.finish(true);
		assertTrue(fixture.calls.stream().noneMatch(call -> call.endsWith(":lobby")), fixture.calls.toString());
	}

	@Test
	void rejectsAProcessThatIsBothPlannedAndARunningPeer() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		var plan = fixture.plan(fixture.spec("server", false)).toBuilder()
				.peer("server", new InetSocketAddress("127.0.0.1", 25001)).build();

		assertThrows(IllegalArgumentException.class, () -> fixture.service().start(plan, fixture.preparation));
		assertTrue(fixture.calls.isEmpty());
	}

	@Test
	void reportsWhetherTheSelectedProviderPreservesClientAddresses() {
		ManagedProcessService service = new ExecutionFixture(directory).service();

		assertTrue(service.preservesClientAddress("fixture"));
		assertTrue(service.connectsEnvironments("fixture"));
		var unknown = assertThrows(IllegalArgumentException.class, () -> service.preservesClientAddress("docker"));
		assertEquals("No execution provider 'docker'. Available: [fixture]", unknown.getMessage());
	}

	@Test
	void releasesAllocatedTargetsWhenLaterAllocationFailsAndPreservesCleanupFailures() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		fixture.failingTarget = "proxy";
		fixture.targetCleanupFailure = new IllegalStateException("target cleanup");
		fixture.sessionCleanupFailure = new IllegalStateException("session cleanup");
		var plan = fixture.plan(fixture.spec("server", false), fixture.spec("proxy", true, "server"));
		assertSame(fixture.failure, assertThrows(IllegalStateException.class, () -> fixture.service().start(plan, fixture.preparation)));
		assertEquals(List.of("open", "session-open", "target:server", "target:proxy", "target-close:server", "session-close", "finish:false"), fixture.calls);
		assertSame(fixture.targetCleanupFailure, fixture.failure.getSuppressed()[0]);
		assertSame(fixture.sessionCleanupFailure, fixture.targetCleanupFailure.getSuppressed()[0]);
	}

	@Test
	void finalizesOnlyTransferredPreparationsAfterPreparationFails() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		fixture.failingPreparation = "proxy";
		var plan = fixture.plan(fixture.spec("server", false), fixture.spec("proxy", true, "server"));
		assertSame(fixture.failure, assertThrows(IllegalStateException.class, () -> fixture.service().start(plan, fixture.preparation)));
		assertFalse(fixture.calls.stream().anyMatch(call -> call.startsWith("configure:")));
		assertTrue(fixture.calls.contains("prepared-finish:server:false"));
		assertFalse(fixture.calls.contains("prepared-finish:proxy:false"));
		assertEquals("finish:false", fixture.calls.getLast());
	}

	@Test
	void rollsBackStartedDependenciesWhenDependentLaunchCreationFails() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		fixture.failingConfiguration = "proxy";
		var plan = fixture.plan(fixture.spec("server", false), fixture.spec("proxy", true, "server"));
		assertSame(fixture.failure, assertThrows(IllegalStateException.class, () -> fixture.service().start(plan, fixture.preparation)));
		assertTrue(fixture.calls.contains("detach:server:1"));
		assertTrue(fixture.calls.containsAll(List.of("attach:server:1", "stop:server")));
		assertFalse(fixture.calls.contains("start:proxy"));
		assertFalse(fixture.calls.contains("detach:proxy:1"));
		assertTrue(fixture.calls.contains("prepared-finish:proxy:false"));
		assertEquals("finish:false", fixture.calls.getLast());
	}

	@Test
	void rollsBackReadyProcessesWhenTheirAttachmentFails() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		fixture.failingAttachment = "server";
		var plan = fixture.plan(fixture.spec("server", false), fixture.spec("proxy", true, "server"));
		assertSame(fixture.failure, assertThrows(IllegalStateException.class, () -> fixture.service().start(plan, fixture.preparation)));
		assertTrue(fixture.calls.containsAll(List.of("detach:server:1", "stop:server")));
		assertFalse(fixture.calls.contains("configure:proxy:1"));
		assertFalse(fixture.calls.contains("start:proxy"));
		assertEquals("finish:false", fixture.calls.getLast());
	}

	@Test
	void attemptsAllCleanupAndPropagatesUnsuccessfulFinalization() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		var plan = fixture.plan(fixture.spec("server", false), fixture.spec("proxy", true, "server"));
		var group = fixture.service().start(plan, fixture.preparation);
		fixture.attachmentCleanupFailure = new IllegalStateException("attachment cleanup");
		fixture.targetCleanupFailure = new IllegalStateException("target cleanup");
		assertSame(fixture.attachmentCleanupFailure, assertThrows(IllegalStateException.class, () -> group.finish(true)));
		assertTrue(fixture.calls.containsAll(List.of("stop:server", "stop:proxy", "session-close",
				"prepared-finish:server:false", "prepared-finish:proxy:false", "finish:false")));
	}

	@Test
	void rejectsMissingOrCyclicDependenciesBeforeOpeningPreparation() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		var unknown = fixture.plan(fixture.spec("server", false, "missing"));
		assertThrows(IllegalArgumentException.class, () -> fixture.service().start(unknown, fixture.preparation));
		var cyclic = fixture.plan(fixture.spec("first", false, "second"), fixture.spec("second", false, "first"));
		assertThrows(IllegalArgumentException.class, () -> fixture.service().start(cyclic, fixture.preparation));
		assertTrue(fixture.calls.isEmpty());
	}
}
