package me.whereareiam.anvil.environment.execution.managed;

import me.whereareiam.anvil.api.process.ProcessGroup;
import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.api.type.ProcessState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ManagedPreparationTest {
	@TempDir
	Path directory;

	@Test
	void preparesWholeTopologyThenStartsOnlySelectedGenerationsAndRetainsTheirInputs() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		List<RunningProcess> observed = new ArrayList<>();
		AtomicReference<ProcessGroup> exposed = new AtomicReference<>();
		var plan = fixture.plan(fixture.spec("proxy", true, "lobby", "game"),
				fixture.spec("lobby", false), fixture.spec("game", false));
		ProcessGroup group = fixture.service().prepare(plan, fixture.preparation, process -> {
			assertEquals(ProcessState.CREATED, process.state());
			assertTrue(process.console().read(0, 10).getLines().isEmpty());
			assertSame(process, exposed.get().get(process.name()));
			observed.add(process);
		});
		exposed.set(group);
		try (group) {
			assertTrue(group.all().isEmpty());
			assertThrows(IllegalStateException.class, () -> group.get("lobby"));
			assertEquals(Set.of("proxy", "lobby", "game"), fixture.preparation.observedPeers.get("proxy").keySet());
			assertTrue(fixture.calls.stream().noneMatch(call -> call.startsWith("configure:") || call.startsWith("start:") || call.startsWith("attach:")));
			group.stop("proxy");
			RunningProcess first = group.start("proxy");
			assertSame(first, group.start("proxy"));
			assertEquals(List.of("configure:proxy:1"), fixture.calls.stream()
					.filter(call -> call.startsWith("configure:")).toList());
			assertEquals(List.of("proxy"), group.all().stream().map(RunningProcess::name).toList());
			group.stop("proxy");
			assertEquals(ProcessState.STOPPED, first.state());
			RunningProcess next = group.start("proxy");
			assertNotSame(first, next);
			assertEquals(first.address(), next.address());
			assertEquals(first.workDirectory(), next.workDirectory());
			assertEquals(1, fixture.calls.stream().filter("prepare:proxy"::equals).count());
			assertEquals(List.of("1", "2"), fixture.targets.get("proxy").commands.stream()
					.map(command -> command.getEnvironment().get("GENERATION")).toList());
			group.startAll();
			assertEquals(3, group.all().size());
			assertSame(next, group.proxy("proxy"));
			assertEquals(4, observed.size());
		}
		assertEquals("finish:true", fixture.calls.getLast());
	}

	@Test
	void launchFailureIsDeferredToSelectedStartAndRetainsInputsForRetry() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		fixture.failingConfiguration = "server";
		var group = fixture.service().prepare(fixture.plan(fixture.spec("server", false)), fixture.preparation, null);
		try (group) {
			assertTrue(group.all().isEmpty());
			assertSame(fixture.failure, assertThrows(IllegalStateException.class, () -> group.start("server")));
			assertTrue(group.all().isEmpty());
			assertFalse(fixture.calls.contains("start:server"));
			assertFalse(fixture.calls.contains("prepared-finish:server:false"));

			fixture.failingConfiguration = null;
			assertEquals(ProcessState.READY, group.start("server").state());
			assertEquals(1, fixture.calls.stream().filter("prepare:server"::equals).count());
			assertEquals("2", fixture.targets.get("server").commands.getFirst().getEnvironment().get("GENERATION"));
		}
		assertFalse(fixture.calls.contains("detach:server:1"));
		assertEquals(1, fixture.calls.stream().filter("detach:server:2"::equals).count());
		assertEquals("finish:false", fixture.calls.getLast());
	}

	@Test
	void rejectedObserverPreventsJvmStartClosesItsConsoleAndPreservesFailureOutcome() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		AtomicReference<RunningProcess> observed = new AtomicReference<>();
		IllegalArgumentException rejected = new IllegalArgumentException("Observation rejected");
		var group = fixture.service().prepare(fixture.plan(fixture.spec("server", false)), fixture.preparation, process -> {
			observed.set(process);
			throw rejected;
		});
		assertSame(rejected, assertThrows(IllegalArgumentException.class, () -> group.start("server")));
		assertFalse(fixture.calls.contains("start:server"));
		assertEquals(ProcessState.STOPPED, observed.get().state());
		assertTrue(observed.get().console().read(0, 10).isClosed());
		group.finish(true);
		assertEquals("finish:false", fixture.calls.getLast());
	}

	@Test
	@Timeout(10)
	void observerCannotFinishTheGroupOrChangeProcessesFromInsideTheStart() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		AtomicReference<ProcessGroup> owner = new AtomicReference<>();
		List<Throwable> rejected = new ArrayList<>();
		var group = fixture.service().prepare(fixture.plan(fixture.spec("server", false)), fixture.preparation, process -> {
			for (Runnable action : List.<Runnable>of(() -> owner.get().stop("server"), () -> owner.get().finish(false))) {
				try {
					action.run();
				} catch (IllegalStateException failure) {
					rejected.add(failure);
				}
			}
			throw new IllegalStateException("Observation aborted");
		});
		owner.set(group);

		assertThrows(IllegalStateException.class, () -> group.start("server"));
		assertEquals(2, rejected.size());
		assertFalse(fixture.calls.contains("start:server"));
		group.finish(true);
		assertEquals("finish:false", fixture.calls.getLast());
	}

	@Test
	@Timeout(10)
	void finishingFromAnotherThreadCancelsAStartThatIsWaitingForReadiness() throws Exception {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		fixture.silent.add("server");
		var group = fixture.service().prepare(fixture.plan(fixture.spec("server", false)), fixture.preparation, null);

		try (var tasks = Executors.newVirtualThreadPerTaskExecutor()) {
			var start = tasks.submit(() -> group.start("server"));
			while (!fixture.calls.contains("start:server")) Thread.onSpinWait();

			long began = System.nanoTime();
			group.finish(false);
			// The fixture's startup timeout is three seconds; cancellation must not wait for it.
			assertTrue(Duration.ofNanos(System.nanoTime() - began).compareTo(Duration.ofSeconds(2)) < 0);

			var failure = assertThrows(ExecutionException.class, () -> start.get(5, TimeUnit.SECONDS));
			assertTrue(failure.getCause().getMessage().contains("cancelled"), failure.getCause().getMessage());
		}
		assertTrue(fixture.calls.contains("stop:server"));
		assertEquals("finish:false", fixture.calls.getLast());
	}

	@Test
	void failureDuringShutdownPreventsSuccessfulProcessAndScenarioFinalization() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		var group = fixture.service().prepare(fixture.plan(fixture.spec("first", false), fixture.spec("second", false)), fixture.preparation, null);
		group.startAll();
		fixture.beforeDetach = () -> fixture.targets.get("second").current.terminate(true);
		group.finish(true);
		assertTrue(fixture.calls.contains("prepared-finish:first:false"));
		assertTrue(fixture.calls.contains("prepared-finish:second:false"));
		assertEquals("finish:false", fixture.calls.getLast());
	}

	@Test
	void replacingAnUnexpectedlyExitedGenerationRetainsItsFailureOutcome() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		var group = fixture.service().prepare(fixture.plan(fixture.spec("server", false)), fixture.preparation, null);
		RunningProcess first = group.start("server");
		fixture.targets.get("server").current.terminate(true);
		assertEquals(ProcessState.FAILED, first.state());
		group.stop("server");
		RunningProcess replacement = group.start("server");
		assertNotNull(first.executionId());
		assertNotEquals(first.executionId(), replacement.executionId());
		assertEquals(ProcessState.READY, replacement.state());
		group.finish(true);
		assertTrue(fixture.calls.contains("prepared-finish:server:false"));
		assertEquals("finish:false", fixture.calls.getLast());
	}

	@Test
	void closingUnstartedPreparationReleasesInputsWithoutCreatingLaunches() {
		ExecutionFixture fixture = new ExecutionFixture(directory);
		var group = fixture.service().prepare(fixture.plan(fixture.spec("server", false)), fixture.preparation, null);
		group.finish(true);
		group.finish(true);
		assertTrue(fixture.calls.stream().noneMatch(call -> call.startsWith("configure:")
				|| call.startsWith("start:") || call.startsWith("detach:")));
		assertEquals(1, fixture.calls.stream().filter("prepared-finish:server:true"::equals).count());
		assertEquals("finish:true", fixture.calls.getLast());
		assertThrows(IllegalStateException.class, () -> group.start("server"));
	}
}
