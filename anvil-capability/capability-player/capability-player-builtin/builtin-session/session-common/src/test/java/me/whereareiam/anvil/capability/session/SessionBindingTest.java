package me.whereareiam.anvil.capability.session;

import me.whereareiam.anvil.player.worker.TestOperations;
import me.whereareiam.anvil.player.worker.TestWorkerPlayer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionBindingTest {
	private final TestWorkerPlayer player = new TestWorkerPlayer("Alice");
	private final TestOperations operations = new TestOperations();

	@Test
	void targetsEveryLibraryAndSessionType() {
		SessionBinding binding = new SessionBinding();

		assertEquals("me.whereareiam.anvil.session", binding.id());
		assertEquals(Optional.empty(), binding.libraryId());
		assertEquals(Object.class, binding.nativeSessionType());
	}

	@Test
	void drivesThePlayerLifecycle() {
		new SessionBinding().bind(player, operations);

		assertNull(operations.call(SessionOperations.CONNECT, null));
		assertTrue(player.connected());
		Object first = player.nativeSession();

		assertNull(operations.call(SessionOperations.REJOIN, null));
		assertNotSame(first, player.nativeSession());

		assertNull(operations.call(SessionOperations.DISCONNECT, null));
		assertFalse(player.connected());
		assertEquals(List.of("session.connect", "session.disconnect", "session.rejoin"), operations.registered());
	}

	@Test
	void leavesConnectionEventsToTheWorker() {
		new SessionBinding().bind(player, operations);

		operations.call(SessionOperations.CONNECT, null);
		operations.call(SessionOperations.DISCONNECT, null);

		assertEquals(List.of(), player.emittedEvents());
	}
}
