package me.whereareiam.anvil.environment.execution.managed;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class StartupSchedulerTest {
	@Test
	void preparesIndependentInputsConcurrently() {
		CountDownLatch together = new CountDownLatch(2);
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> new StartupScheduler(2, 1024).run(List.of(1, 2), input -> {
			together.countDown();
			try {
				assertTrue(together.await(3, TimeUnit.SECONDS));
			} catch (InterruptedException failure) {
				Thread.currentThread().interrupt();
				throw new AssertionError(failure);
			}
		}));
	}

	@Test
	void preservesFailureAndDoesNotBeginQueuedPreparations() {
		AtomicInteger started = new AtomicInteger();
		RuntimeException original = new IllegalStateException("preparation failed");
		assertSame(original, assertThrows(RuntimeException.class, () -> new StartupScheduler(1, 1024).run(List.of(1, 2, 3), input -> {
			started.incrementAndGet();
			throw original;
		})));
		assertEquals(1, started.get());
	}
}
