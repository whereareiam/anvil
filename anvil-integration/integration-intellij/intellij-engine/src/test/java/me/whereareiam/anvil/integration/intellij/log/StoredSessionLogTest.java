package me.whereareiam.anvil.integration.intellij.log;

import com.intellij.openapi.util.Disposer;

import java.util.ArrayList;
import java.util.List;

import me.whereareiam.anvil.integration.intellij.model.SessionLogEntry;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoredSessionLogTest {
	@Test
	void lateSubscribersReceiveRetainedOutputThenOrderedLiveEvents() {
		StoredSessionLog log = new StoredSessionLog();
		log.append(null, null, 0, "Preparing\n", false);
		RecordingListener listener = new RecordingListener();
		var owner = Disposer.newDisposable();
		try {
			log.subscribe(listener, owner);
			log.append("server", null, 1, "Ready\n", false);
			log.finish(0);

			assertEquals(List.of("clear", "Preparing\n", "Ready\n", "finish:0"), listener.events);
			assertEquals(0, log.getExitCode());
		} finally {
			Disposer.dispose(owner);
		}
	}

	@Test
	void clearReplacesRetainedHistoryAndDisposedSubscribersStopReceivingEvents() {
		StoredSessionLog log = new StoredSessionLog();
		RecordingListener first = new RecordingListener();
		var owner = Disposer.newDisposable();
		log.subscribe(first, owner);
		log.append(null, null, 0, "old\n", false);
		log.clear();
		log.append(null, null, 0, "current\n", false);
		Disposer.dispose(owner);
		log.append(null, null, 0, "retained\n", false);

		RecordingListener replay = new RecordingListener();
		log.replay(replay);
		assertEquals(List.of("clear", "current\n", "retained\n"), replay.events);
		assertNull(log.getExitCode());
	}

	@Test
	void historyKeepsTheNewestOutputWithinItsBudgetAndReportsDiscardedEntries() {
		StoredSessionLog log = new StoredSessionLog(10);
		RecordingListener live = new RecordingListener();
		var owner = Disposer.newDisposable();
		try {
			log.subscribe(live, owner);
			log.append("server", null, 1, "first\n", false);
			log.append("server", null, 2, "second\n", false);
			log.append("server", null, 3, "third\n", false);

			assertEquals(List.of("clear", "first\n", "second\n", "third\n"), live.events);
		} finally {
			Disposer.dispose(owner);
		}

		RecordingListener replay = new RecordingListener();
		log.replay(replay);
		assertEquals(List.of(
				"clear",
				"[Anvil] 2 earlier output entries were discarded to limit memory use.\n",
				"third\n"
		), replay.events);

		log.clear();
		RecordingListener cleared = new RecordingListener();
		log.replay(cleared);
		assertEquals(List.of("clear"), cleared.events);
	}

	private static final class RecordingListener implements SessionLogListener {
		private final List<String> events = new ArrayList<>();

		@Override
		public void cleared() {
			events.add("clear");
		}

		@Override
		public void appended(@NotNull SessionLogEntry entry) {
			events.add(entry.getText());
		}

		@Override
		public void finished(int exitCode) {
			events.add("finish:" + exitCode);
		}
	}
}
