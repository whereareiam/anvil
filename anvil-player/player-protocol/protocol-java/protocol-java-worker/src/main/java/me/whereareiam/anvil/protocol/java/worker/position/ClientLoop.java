package me.whereareiam.anvil.protocol.java.worker.position;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The game loop of one player's client: once started, a virtual thread runs a tick every 50 milliseconds, as the
 * vanilla client's loop ticks 20 times a second and catches up at most ten ticks it fell behind
 * ({@code Minecraft.runTick}). The loop holds one lock for each tick and for each packet and operation it
 * {@link #run runs}, so they apply between ticks, as the vanilla client runs its packets on its own thread. A tick that
 * throws stops the loop and hands its failure to the owner once; closing sets its flag under the same lock, so a tick
 * that waited for the lock never runs after it.
 */
final class ClientLoop implements AutoCloseable {
	/**
	 * The time between two ticks of the vanilla client, which ticks 20 times a second.
	 */
	static final Duration PERIOD = Duration.ofMillis(50);
	private static final int MAX_CATCH_UP = 10;

	private final long period;
	private final @NotNull ReentrantLock lock = new ReentrantLock();
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
	ClientLoop(
			@NotNull String name,
			@NotNull Duration period,
			@NotNull Runnable tick,
			@NotNull Consumer<Throwable> failed
	) {
		this.period = period.toNanos();
		this.name = name;
		this.tick = tick;
		this.failed = failed;
	}

	/**
	 * Runs a packet's, an operation's or a tick's change under the loop's lock, between ticks.
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
	 * Reads or changes the client under the loop's lock, between ticks.
	 *
	 * @param reading what reads or changes it
	 * @param <T> what it answers
	 * @return its answer
	 */
	<T> T get(@NotNull Supplier<? extends T> reading) {
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
		run(() -> {
			if (closed) throw new IllegalStateException("The client of " + name + " is closed");
			if (current != null) return;

			Run run = new Run();
			current = run;
			run.thread = Thread.ofVirtual().name("anvil-client-" + name).start(run);
		});
	}

	/**
	 * Stops the loop for good: no tick runs after this returns.
	 */
	@Override
	public void close() {
		run(() -> {
			closed = true;
			Run run = current;
			current = null;
			if (run != null) run.end();
		});
	}

	/**
	 * One start of the loop, which ends for good when the loop closes or a tick fails.
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
