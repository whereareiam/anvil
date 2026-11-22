package me.whereareiam.anvil.player.library.mcprotocol.worker.host;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PlayerChannelEventDispatcherTest {
	/**
	 * More events than a release sends for a teleport's chunks with a tap of every packet open.
	 */
	private static final int BURST = 5000;
	private static final Duration STALL = Duration.ofMillis(200);

	@Test
	void queuedEventsUseTheirOriginalActiveSubscriptions() throws Exception {
		List<String> observed = new CopyOnWriteArrayList<>();
		var entered = new CountDownLatch(1);
		var proceed = new CountDownLatch(1);
		try (var events = new PlayerEventDispatcher("Alice", ignored -> { })) {
			events.subscribe("gate", ignored -> { entered.countDown(); await(proceed); });
			var original = events.subscribe("changed", payload -> observed.add("original"));
			events.dispatch("gate", JsonNodeFactory.instance.objectNode());
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			events.dispatch("changed", JsonNodeFactory.instance.objectNode().put("value", 1));
			original.close();
			events.subscribe("changed", payload -> observed.add("new " + payload.path("value").asInt()));
			proceed.countDown();
			events.dispatch("changed", JsonNodeFactory.instance.objectNode().put("value", 2));
			events.stop();
			assertEquals(List.of("new 2"), observed);
		} finally { proceed.countDown(); }
	}

	@Test
	void burstArrivingWhileACallbackRunsIsDeliveredInFull() throws Exception {
		var entered = new CountDownLatch(1);
		var proceed = new CountDownLatch(1);
		var delivered = new CountDownLatch(BURST);
		try (var events = new PlayerEventDispatcher("Alice", ignored -> { })) {
			events.subscribe("gate", ignored -> { entered.countDown(); await(proceed); });
			events.subscribe("frame", ignored -> delivered.countDown());
			events.dispatch("gate", JsonNodeFactory.instance.objectNode());
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			for (int i = 0; i < BURST; i++) events.dispatch("frame", JsonNodeFactory.instance.objectNode());
			proceed.countDown();
			assertTrue(delivered.await(3, TimeUnit.SECONDS));
			assertDoesNotThrow(events::throwIfFailed);
		} finally { proceed.countDown(); }
	}

	@Test
	void stalledCallbackFailsOnlyItsPlayer() throws Exception {
		var entered = new CountDownLatch(1);
		var proceed = new CountDownLatch(1);
		var delivered = new CountDownLatch(1);
		try (var slow = new PlayerEventDispatcher("Slow", ignored -> { }, STALL);
			 var other = new PlayerEventDispatcher("Other", ignored -> { }, STALL)
		) {
			slow.subscribe("changed", ignored -> { entered.countDown(); await(proceed); });
			slow.dispatch("changed", JsonNodeFactory.instance.objectNode());
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			slow.dispatch("changed", JsonNodeFactory.instance.objectNode());
			assertDoesNotThrow(slow::throwIfFailed);
			Thread.sleep(STALL.multipliedBy(2).toMillis());
			slow.dispatch("changed", JsonNodeFactory.instance.objectNode());
			IllegalStateException failure = assertThrows(IllegalStateException.class, slow::throwIfFailed);
			assertTrue(failure.getMessage().contains("Slow"));
			assertTrue(failure.getMessage().contains("capability events waited"));
			other.subscribe("changed", ignored -> delivered.countDown());
			other.dispatch("changed", JsonNodeFactory.instance.objectNode());
			assertTrue(delivered.await(3, TimeUnit.SECONDS));
			assertDoesNotThrow(other::throwIfFailed);
			proceed.countDown();
			IllegalStateException closing = assertThrows(IllegalStateException.class, slow::close);
			assertEquals(failure.getMessage(), closing.getMessage());
		} finally { proceed.countDown(); }
	}

	@Test
	void everyCallerGetsItsOwnExceptionForTheFailure() throws Exception {
		var entered = new CountDownLatch(1);
		var proceed = new CountDownLatch(1);
		try (var events = new PlayerEventDispatcher("Alice", ignored -> { }, STALL)) {
			events.subscribe("changed", ignored -> { entered.countDown(); await(proceed); });
			events.dispatch("changed", JsonNodeFactory.instance.objectNode());
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			events.dispatch("changed", JsonNodeFactory.instance.objectNode());
			Thread.sleep(STALL.multipliedBy(2).toMillis());
			events.dispatch("changed", JsonNodeFactory.instance.objectNode());

			IllegalStateException waiting = assertThrows(IllegalStateException.class, events::throwIfFailed);
			IllegalStateException cleanup = assertThrows(IllegalStateException.class, events::throwIfFailed);
			assertNotSame(waiting, cleanup);
			assertSame(waiting.getCause(), cleanup.getCause());
			waiting.addSuppressed(cleanup);
			proceed.countDown();
			assertThrows(IllegalStateException.class, events::close);
		} finally { proceed.countDown(); }
	}

	@Test
	void closingInsideCallbackCancelsQueuedNormalEventsAndNotifiesDestructionAfterwards() throws Exception {
		List<String> observed = new CopyOnWriteArrayList<>();
		var entered = new CountDownLatch(1);
		var proceed = new CountDownLatch(1);
		var destroyed = new CountDownLatch(1);
		try (var events = new PlayerEventDispatcher("Alice", ignored -> { })) {
			events.subscribe("changed", ignored -> {
				entered.countDown();
				await(proceed);
				events.close();
				observed.add("callback returned");
			});
			events.subscribe("changed", ignored -> observed.add("later normal listener"));
			events.subscribe("player.destroyed", ignored -> { observed.add("destroyed"); destroyed.countDown(); });
			events.dispatch("changed", JsonNodeFactory.instance.objectNode());
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			events.dispatch("changed", JsonNodeFactory.instance.objectNode());
			proceed.countDown();
			assertTrue(destroyed.await(3, TimeUnit.SECONDS));
			assertEquals(List.of("callback returned", "destroyed"), observed);
			assertDoesNotThrow(events::throwIfFailed);
		} finally { proceed.countDown(); }
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("callback gate timed out");
		} catch (InterruptedException interrupted) {
			Thread.currentThread().interrupt();
		}
	}
}
