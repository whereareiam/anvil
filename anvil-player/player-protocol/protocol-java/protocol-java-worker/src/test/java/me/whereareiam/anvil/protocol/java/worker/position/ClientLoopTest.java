package me.whereareiam.anvil.protocol.java.worker.position;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientLoopTest {
	private final AtomicInteger ticks = new AtomicInteger();
	private final List<Throwable> failures = new CopyOnWriteArrayList<>();
	private final AtomicReference<Runnable> tick = new AtomicReference<>(ticks::incrementAndGet);
	private final ClientLoop loop = new ClientLoop("Alice", Duration.ofMillis(5), () -> tick.get().run(), failures::add);

	@AfterEach
	void close() {
		loop.close();
	}

	@Test
	void ticksOnAVirtualThreadNamedAfterThePlayerUntilClosed() {
		AtomicReference<Thread> thread = new AtomicReference<>();
		tick.set(() -> {
			thread.set(Thread.currentThread());
			ticks.incrementAndGet();
		});

		loop.start();
		awaitTicks(count -> count >= 3);
		loop.close();
		int closed = ticks.get();

		assertTrue(thread.get().isVirtual());
		assertEquals("anvil-client-Alice", thread.get().getName());
		sleep(50);
		assertEquals(closed, ticks.get());
	}

	@Test
	void stopsAtAFailedTickAndReportsItOnce() {
		RuntimeException failure = new IllegalStateException("broken tick");
		AtomicBoolean broken = new AtomicBoolean();
		tick.set(() -> {
			ticks.incrementAndGet();
			if (broken.get()) throw failure;
		});
		loop.start();
		awaitTicks(count -> count >= 1);

		broken.set(true);
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (failures.isEmpty()) {
			assertTrue(System.nanoTime() < deadline, "the tick did not fail");
			sleep(1);
		}
		int stopped = ticks.get();
		sleep(30);

		assertEquals(List.of(failure), failures);
		assertEquals(stopped, ticks.get());
	}

	@Test
	void runsNoTickThatWaitedForTheLockAfterItClosed() {
		loop.run(() -> {
			loop.start();
			sleep(30);
			loop.close();
		});

		sleep(50);
		assertEquals(0, ticks.get());
		assertThrows(IllegalStateException.class, loop::start);
	}

	@Test
	void runsWhatItIsHandedBetweenTicksAndAnswersWhatItReads() {
		loop.run(ticks::incrementAndGet);

		assertEquals(1, (int) loop.get(ticks::get));
	}

	private void awaitTicks(IntPredicate reached) {
		long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
		while (!reached.test(ticks.get())) {
			assertTrue(System.nanoTime() < deadline, "the loop did not tick");
			sleep(1);
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}
}
