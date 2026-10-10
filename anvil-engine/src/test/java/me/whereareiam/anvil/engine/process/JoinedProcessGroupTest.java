package me.whereareiam.anvil.engine.process;

import me.whereareiam.anvil.api.process.RunningProcess;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JoinedProcessGroupTest {
	private final List<String> calls = new ArrayList<>();
	private final RecordingGroup own = new RecordingGroup("own", calls, "proxy");
	private final RecordingGroup retained = new RecordingGroup("retained", calls, "lobby");
	private final JoinedProcessGroup joined = new JoinedProcessGroup(own, retained, Set.of("lobby"),
			successful -> calls.add("release:" + successful));

	@Test
	void listsRetainedProcessesBeforeTheScenariosOwnAndFindsEachOnItsSide() {
		assertEquals(List.of("lobby", "proxy"), joined.all().stream().map(RunningProcess::name).toList());
		assertEquals("lobby", joined.get("lobby").name());
		assertEquals("proxy", joined.get("proxy").name());
	}

	@Test
	void startsRetainedProcessesBeforeTheProcessesThatConnectToThem() {
		joined.startAll();

		assertEquals(List.of("retained:startAll", "own:startAll"), calls);
	}

	@Test
	void changesAProcessOnTheSideThatOwnsIt() {
		joined.restart("lobby");
		joined.stop("proxy");
		joined.start("proxy");

		assertEquals(List.of("retained:restart:lobby", "own:stop:proxy", "own:start:proxy"), calls);
	}

	@Test
	void finishesItsOwnProcessesAndHandsTheRetainedOnesBackOnce() {
		joined.finish(true);
		joined.finish(true);

		assertEquals(List.of("own:finish:true", "release:true"), calls);
	}

	@Test
	void handsTheRetainedProcessesBackAsFailedWhenTheScenarioOrItsCleanupFailed() {
		joined.finish(false);
		assertEquals(List.of("own:finish:false", "release:false"), calls);

		calls.clear();
		RecordingGroup failing = new RecordingGroup("own", calls, "proxy");
		failing.finishFailure = new IllegalStateException("cleanup");
		var other = new JoinedProcessGroup(failing, retained, Set.of("lobby"), successful -> calls.add("release:" + successful));

		assertSame(failing.finishFailure, assertThrows(IllegalStateException.class, () -> other.finish(true)));
		assertEquals(List.of("own:finish:true", "release:false"), calls);
	}

	@Test
	void refusesToChangeARetainedProcessAfterTheScenarioFinished() {
		joined.finish(true);
		calls.clear();

		assertThrows(IllegalStateException.class, () -> joined.stop("lobby"));
		assertThrows(IllegalStateException.class, () -> joined.restart("lobby"));
		assertEquals(List.of(), calls);
	}
}
