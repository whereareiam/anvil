package me.whereareiam.anvil.protocol.mcprotocol.worker.host;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class PlayerChannelEventDispatcherTest {
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
	void pendingOverflowFailsOnlyTheSlowPlayerAndRemainsBounded() throws Exception {
		var entered = new CountDownLatch(1);
		var proceed = new CountDownLatch(1);
		var delivered = new CountDownLatch(1);
		try (var slow = new PlayerEventDispatcher("Slow", ignored -> { });
			 var other = new PlayerEventDispatcher("Other", ignored -> { })
		) {
			slow.subscribe("changed", ignored -> { entered.countDown(); await(proceed); });
			slow.dispatch("changed", JsonNodeFactory.instance.objectNode());
			assertTrue(entered.await(3, TimeUnit.SECONDS));
			for (int i = 0; i < 1000; i++) slow.dispatch("changed", JsonNodeFactory.instance.objectNode());
			IllegalStateException failure = assertThrows(IllegalStateException.class, slow::throwIfFailed);
			assertTrue(failure.getMessage().contains("Slow"));
			assertTrue(failure.getMessage().contains("pending capability events"));
			other.subscribe("changed", ignored -> delivered.countDown());
			other.dispatch("changed", JsonNodeFactory.instance.objectNode());
			assertTrue(delivered.await(3, TimeUnit.SECONDS));
			assertDoesNotThrow(other::throwIfFailed);
			proceed.countDown();
			assertSame(failure, assertThrows(IllegalStateException.class, slow::close));
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
