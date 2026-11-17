package me.whereareiam.anvil.capability.movement.java.physics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicsLoopTest {
	private final AtomicInteger ticks = new AtomicInteger();
	private final List<Throwable> failures = new CopyOnWriteArrayList<>();
	private final AtomicReference<Runnable> tick = new AtomicReference<>(ticks::incrementAndGet);
	private final PhysicsLoop loop = new PhysicsLoop("Alice", Duration.ofMillis(5), () -> tick.get().run(), failures::add);

	@AfterEach
	void close() {
		loop.close();
	}

	@Test
	void ticksOnAVirtualThreadNamedAfterThePlayerUntilStopped() {
		AtomicReference<Thread> thread = new AtomicReference<>();
		tick.set(() -> {
			thread.set(Thread.currentThread());
			ticks.incrementAndGet();
		});

		loop.start();
		loop.await(() -> ticks.get() >= 3, Duration.ofSeconds(5), "stopped");
		loop.stop();
		int stopped = ticks.get();

		assertTrue(thread.get().isVirtual());
		assertEquals("anvil-physics-Alice", thread.get().getName());
		assertFalse(loop.isRunning());
		sleep(50);
		assertEquals(stopped, ticks.get());
	}

	@Test
	void stopsAtAFailedTickReportsItOnceAndWakesItsWaiters() throws InterruptedException {
		RuntimeException failure = new IllegalStateException("broken tick");
		AtomicReference<Boolean> broken = new AtomicReference<>(false);
		tick.set(() -> {
			if (broken.get()) throw failure;
		});
		loop.start();
		CountDownLatch woken = new CountDownLatch(1);
		AtomicReference<IllegalStateException> waited = new AtomicReference<>();
		Thread waiter = Thread.ofVirtual().start(() -> {
			try {
				loop.await(() -> false, Duration.ofSeconds(30), "turned off");
			} catch (IllegalStateException exception) {
				waited.set(exception);
			}
			woken.countDown();
		});
		while (waiter.getState() != Thread.State.WAITING && waiter.getState() != Thread.State.TIMED_WAITING) sleep(1);

		broken.set(true);

		assertTrue(woken.await(5, TimeUnit.SECONDS));
		assertEquals("turned off", waited.get().getMessage());
		sleep(30);
		assertEquals(List.of(failure), failures);
		assertFalse(loop.isRunning());
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
		assertFalse(loop.isRunning());
		assertThrows(IllegalStateException.class, loop::start);
	}

	@Test
	void runsNoTickAfterItStopped() {
		loop.run(() -> {
			loop.start();
			sleep(30);
			loop.stop();
		});

		sleep(50);
		assertEquals(0, ticks.get());
	}

	@Test
	void endsAWaitThatDoesNotReachItsTickInTime() {
		IllegalStateException failure = assertThrows(IllegalStateException.class, () -> {
			loop.start();
			loop.await(() -> false, Duration.ofMillis(30), "stopped");
		});

		assertTrue(failure.getMessage().contains("did not reach"), failure.getMessage());
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
