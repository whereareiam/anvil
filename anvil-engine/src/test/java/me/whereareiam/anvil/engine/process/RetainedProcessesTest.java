package me.whereareiam.anvil.engine.process;

import me.whereareiam.anvil.api.type.ProcessState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetainedProcessesTest {
	private final List<String> calls = new ArrayList<>();
	private final RetainedProcesses<RecordingGroup> retained = new RetainedProcesses<>();
	private int started;

	@Test
	void lendsAnIdleGroupToTheNextScenarioOfTheSameIdentity() {
		RecordingGroup first = retained.lease("backends", this::start);
		retained.release(first, true);

		assertSame(first, retained.lease("backends", this::start));
		assertEquals(1, started);
		assertEquals(List.of(), calls, "A kept group is neither stopped nor started again");
	}

	@Test
	void startsAGroupOfItsOwnForAScenarioThatRunsAtTheSameTimeOrDeclaresOtherProcesses() {
		RecordingGroup first = retained.lease("backends", this::start);

		assertNotSame(first, retained.lease("backends", this::start));
		retained.release(first, true);
		assertNotSame(first, retained.lease("other", this::start));
		assertEquals(3, started);
	}

	@Test
	void stopsTheGroupOfAFailedScenarioAndFinalizesItAsFailed() {
		RecordingGroup group = retained.lease("backends", this::start);
		retained.release(group, false);

		assertEquals(List.of("group-1:finish:false"), calls);
		assertNotSame(group, retained.lease("backends", this::start));
	}

	@Test
	void stopsAGroupThatASuccessfulScenarioDidNotLeaveReady() {
		RecordingGroup group = retained.lease("backends", this::start);
		group.state = ProcessState.STOPPED;
		retained.release(group, true);

		assertEquals(List.of("group-1:finish:false"), calls);
	}

	@Test
	void stopsAnIdleGroupThatDiedInsteadOfLendingIt() {
		RecordingGroup group = retained.lease("backends", this::start);
		retained.release(group, true);
		group.state = ProcessState.FAILED;

		assertNotSame(group, retained.lease("backends", this::start));
		assertEquals(List.of("group-1:finish:false"), calls);
	}

	@Test
	void closingStopsIdleGroupsAndALentGroupStopsWhenItIsReleased() {
		RecordingGroup idle = retained.lease("backends", this::start);
		RecordingGroup lent = retained.lease("backends", this::start);
		retained.release(idle, true);

		retained.close();
		retained.close();
		assertEquals(List.of("group-1:finish:true"), calls);

		retained.release(lent, true);
		assertEquals(List.of("group-1:finish:true", "group-2:finish:true"), calls);
		assertThrows(IllegalStateException.class, () -> retained.lease("backends", this::start));
	}

	@Test
	void closingAttemptsEveryGroupAndKeepsTheFirstFailure() {
		RecordingGroup first = retained.lease("one", this::start);
		RecordingGroup second = retained.lease("two", this::start);
		first.finishFailure = new IllegalStateException("first");
		second.finishFailure = new IllegalStateException("second");
		retained.release(first, true);
		retained.release(second, true);

		RuntimeException failure = assertThrows(RuntimeException.class, retained::close);

		assertEquals(2, calls.size());
		assertEquals(1, failure.getSuppressed().length);
	}

	@Test
	void ignoresAGroupItDidNotLend() {
		retained.release(new RecordingGroup("stranger", calls, "server"), true);

		assertEquals(List.of(), calls);
	}

	private RecordingGroup start() {
		return new RecordingGroup("group-" + ++started, calls, "server");
	}
}
