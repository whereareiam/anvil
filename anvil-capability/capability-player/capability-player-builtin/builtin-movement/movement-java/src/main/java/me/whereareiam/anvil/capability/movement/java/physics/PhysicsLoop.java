package me.whereareiam.anvil.capability.movement.java.physics;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The game loop of one player's client physics: a virtual thread its binding owns runs a tick every 50 milliseconds,
 * as the vanilla client's loop ticks 20 times a second and catches up at most ten ticks it fell behind
 * ({@code Minecraft.runTick}). The loop holds one lock for each tick and for each packet and operation it
 * {@link #run runs}, so they apply between ticks, as the vanilla client runs its packets.
 * A tick that throws stops the loop and hands its failure to the owner once; stopping and closing set their flag under
 * the same lock, so a tick that waited for the lock never runs after them. Every tick, stop and failure wakes the
 * threads waiting for the loop.
 */
final class PhysicsLoop implements AutoCloseable {
	/**
	 * The time between two ticks of the vanilla client, which ticks 20 times a second.
	 */
	static final Duration PERIOD = Duration.ofMillis(50);
	private static final int MAX_CATCH_UP = 10;

	private final long period;
	private final @NotNull ReentrantLock lock;
	private final @NotNull Condition changed;
	private final @NotNull String name;
	private final @NotNull Runnable tick;
	private final @NotNull Consumer<Throwable> failed;
	private @Nullable Run current;
	private boolean closed;

	/**
	 * Creates the stopped loop of a player.
	 *
	 * @param name the player's name, which names the loop's thread
	 * @param period the time between two ticks, {@link #PERIOD} for the vanilla client's
	 * @param tick one tick, run under the lock
	 * @param failed told under the lock why a tick failed, after the loop stopped
	 */
	PhysicsLoop(
			@NotNull String name,
			@NotNull Duration period,
			@NotNull Runnable tick,
			@NotNull Consumer<Throwable> failed
	) {
		this.period = period.toNanos();
		this.lock = new ReentrantLock();
		this.changed = lock.newCondition();
		this.name = name;
		this.tick = tick;
		this.failed = failed;
	}

	/**
	 * Runs a packet's or an operation's change between ticks.
	 *
	 * @param command the change
	 */
	void run(@NotNull Runnable command) {
		lock.lock();
		try {
			command.run();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Runs an operation that answers nothing under the loop's lock, as the channel expects of an operation whose
	 * answer is {@code Void}.
	 *
	 * @param command the operation
	 * @return nothing
	 */
	@Nullable Void answering(@NotNull Runnable command) {
		run(command);
		return null;
	}

	/**
	 * Reads or changes the movement between ticks.
	 *
	 * @param reading what reads or changes it
	 * @param <T> what it answers
	 * @return its answer
	 */
	<T> T get(@NotNull Supplier<T> reading) {
		lock.lock();
		try {
			return reading.get();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Starts the loop's thread, unless it runs.
	 *
	 * @throws IllegalStateException when the loop is closed
	 */
	void start() {
		lock.lock();
		try {
			if (closed) throw new IllegalStateException("The client physics of " + name + " is closed");
			if (current != null) return;

			Run run = new Run();
			current = run;
			run.thread = Thread.ofVirtual().name("anvil-physics-" + name).start(run);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Stops the loop: no tick runs after this returns.
	 */
	void stop() {
		lock.lock();
		try {
			Run run = current;
			current = null;
			if (run != null) run.end();
			changed.signalAll();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Stops the loop for good.
	 */
	@Override
	public void close() {
		lock.lock();
		try {
			closed = true;
			stop();
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Tells whether the loop runs.
	 *
	 * @return whether ticks run
	 */
	boolean isRunning() {
		lock.lock();
		try {
			return current != null;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Waits until a condition of the ticks holds, checking it after each tick.
	 *
	 * @param reached the condition, checked under the lock
	 * @param timeout how long to wait
	 * @param stopped why the wait ends when the loop stops first
	 * @throws IllegalStateException when the loop stops first or the condition does not hold in time
	 */
	void await(@NotNull BooleanSupplier reached, @NotNull Duration timeout, @NotNull String stopped) {
		lock.lock();
		try {
			long remaining = timeout.toNanos();
			while (!reached.getAsBoolean()) {
				if (current == null) throw new IllegalStateException(stopped);
				if (remaining <= 0L) throw new IllegalStateException("The client physics of " + name + " did not reach "
						+ "the awaited tick within " + timeout);

				remaining = changed.awaitNanos(remaining);
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for the client physics of " + name, exception);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * One start of the loop, which ends for good when the loop stops.
	 */
	private final class Run implements Runnable {
		private boolean active = true;
		private @Nullable Thread thread;

		@Override
		public void run() {
			long next = System.nanoTime();
			while (sleepUntil(next += period)) {
				long now = System.nanoTime();
				if (now - next > MAX_CATCH_UP * period) next = now - MAX_CATCH_UP * period;
				if (!tick()) return;
			}
		}

		private boolean tick() {
			lock.lock();
			try {
				if (!active || closed) return false;

				tick.run();
				return true;
			} catch (Throwable failure) {
				current = null;
				end();
				failed.accept(failure);
				return false;
			} finally {
				changed.signalAll();
				lock.unlock();
			}
		}

		/**
		 * Ends this run under the lock; a sleeping thread wakes and leaves.
		 */
		private void end() {
			active = false;
			Thread running = thread;
			if (running != null && running != Thread.currentThread()) running.interrupt();
		}

		private boolean sleepUntil(long deadline) {
			long delay = deadline - System.nanoTime();
			if (delay <= 0L) return true;

			try {
				TimeUnit.NANOSECONDS.sleep(delay);
				return true;
			} catch (InterruptedException exception) {
				return false;
			}
		}
	}
}
