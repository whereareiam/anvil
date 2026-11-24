package me.whereareiam.anvil.capability.ticks;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.api.model.Condition;
import me.whereareiam.anvil.capability.api.exception.ChannelUnavailableException;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.process.RequestChannel;
import me.whereareiam.anvil.capability.ticks.agent.TicksOperations;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickCount;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickWait;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ticks capability of one server, over the operations its ticks agent extension answers. A wait asks the agent for
 * the server's next tick, which the agent answers as soon as the server completes one, so a wait follows the server's
 * ticks without sleeping; it asks for at most a second at a time, so a frozen server still lets it give up in time.
 *
 * <p>A sprint the scenario started and did not see to its end is stopped when the scenario finishes, so a server kept
 * for later scenarios runs at its own pace again.</p>
 */
@RequiredArgsConstructor
final class AgentTicks implements Ticks {
	private static final Duration LONGEST_WAIT = Duration.ofSeconds(1);
	private static final ChannelOperation<Void, AgentTickCount> COUNT = channel(TicksOperations.COUNT);
	private static final ChannelOperation<AgentTickWait, AgentTickCount> NEXT = channel(TicksOperations.NEXT);
	private static final ChannelOperation<Integer, AgentTickCount> SPRINT = channel(TicksOperations.SPRINT);
	private static final ChannelOperation<Void, Boolean> STOP = channel(TicksOperations.STOP);

	private final @NotNull String process;
	private final @NotNull RequestChannel channel;
	private final AtomicBoolean sprinting = new AtomicBoolean();

	@Override
	public long tick() {
		return count().getTick();
	}

	@Override
	public void await(int ticks, @NotNull Duration timeout) {
		positive(ticks);
		positive(timeout);
		Progress progress = new Progress(count(), timeout);
		if (progress.reach(ticks)) return;

		throw new IllegalStateException("'" + process + "' ran " + progress.elapsed + " of " + ticks + " ticks within " + timeout);
	}

	@Override
	public void sprint(int ticks, @NotNull Duration timeout) {
		positive(ticks);
		positive(timeout);
		Progress progress = new Progress(startSprint(ticks), timeout);
		if (progress.reach(ticks)) {
			sprinting.set(false);
			return;
		}

		IllegalStateException failure = new IllegalStateException("'" + process + "' sprinted " + progress.elapsed + " of "
				+ ticks + " ticks within " + timeout);
		stopAfter(failure);
		throw failure;
	}

	@Override
	public <T> T until(@NotNull Condition<T> condition, @NotNull Duration timeout) {
		positive(timeout);
		return watch(condition, new Progress(count(), timeout), Long.MAX_VALUE);
	}

	@Override
	public <T> T sprintUntil(@NotNull Condition<T> condition, int ticks, @NotNull Duration timeout) {
		positive(ticks);
		positive(timeout);
		Progress progress = new Progress(startSprint(ticks), timeout);
		T held;
		try {
			held = watch(condition, progress, ticks);
		} catch (RuntimeException failure) {
			stopAfter(failure);
			throw failure;
		}

		stop();
		return held;
	}

	/**
	 * Stops a sprint the finished scenario started and did not see to its end. A server that has stopped runs no sprint.
	 */
	void finishScenario() {
		if (!sprinting.get()) return;

		try {
			stop();
		} catch (ChannelUnavailableException stopped) {
			// The server stopped, and its sprint with it.
		}
	}

	/**
	 * Checks a condition now and after each tick the server completes, until it holds, the server has run a number of
	 * ticks, or the time is up.
	 */
	private <T> T watch(@NotNull Condition<T> condition, @NotNull Progress progress, long ticks) {
		while (true) {
			T seen = condition.observe();
			if (condition.holds(seen)) return seen;
			if (progress.elapsed >= ticks || !progress.step())
				throw new IllegalStateException("Gave up on '" + process + "' after " + progress + " waiting until "
						+ condition.getDescription() + "; last seen: " + seen);
		}
	}

	private @NotNull AgentTickCount count() {
		return Objects.requireNonNull(channel.request(COUNT, null));
	}

	private @NotNull AgentTickCount startSprint(int ticks) {
		sprinting.set(true);
		return Objects.requireNonNull(channel.request(SPRINT, ticks));
	}

	private void stop() {
		sprinting.set(false);
		channel.request(STOP, null);
	}

	/**
	 * Stops the sprint of a wait that failed, keeping a failure to stop it as suppressed.
	 */
	private void stopAfter(@NotNull RuntimeException failure) {
		try {
			stop();
		} catch (RuntimeException stopping) {
			failure.addSuppressed(stopping);
		}
	}

	/**
	 * Refuses a wait for no ticks before anything reaches the server, such as a sprint starting; the timeout is refused
	 * the same way.
	 */
	private static void positive(int ticks) {
		if (ticks <= 0) throw new IllegalArgumentException("ticks must be positive");
	}

	private static void positive(@NotNull Duration timeout) {
		if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
	}

	private static <Q, R> @NotNull ChannelOperation<Q, R> channel(@NotNull AgentOperation<Q, R> operation) {
		return new ChannelOperation<>(operation.getName(), operation.getRequestType(), operation.getResponseType());
	}

	/**
	 * How far one wait has come: the ticks the server ran since it began, counted across a restart from the first tick
	 * of the new process, and the time it has left.
	 */
	private final class Progress {
		private final long started = System.nanoTime();
		private final long deadline;
		private AgentTickCount seen;
		private long elapsed;

		private Progress(@NotNull AgentTickCount start, @NotNull Duration timeout) {
			this.seen = start;
			this.deadline = started + timeout.toNanos();
		}

		/**
		 * Waits until the server has run a number of ticks since the wait began, or the time is up.
		 */
		private boolean reach(long ticks) {
			while (elapsed < ticks)
				if (!step()) return false;

			return true;
		}

		/**
		 * Waits for the server's next tick, unless the time is up.
		 */
		private boolean step() {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0) return false;

			long wait = Math.min(Duration.ofNanos(remaining).toMillis(), LONGEST_WAIT.toMillis());
			AgentTickCount next = Objects.requireNonNull(channel.request(NEXT, AgentTickWait.builder()
					.run(seen.getRun())
					.after(seen.getTick())
					.waitMillis(wait)
					.build()));
			elapsed += next.getRun().equals(seen.getRun()) ? next.getTick() - seen.getTick() : next.getTick();
			seen = next;

			return true;
		}

		@Override
		public String toString() {
			double seconds = (System.nanoTime() - started) / 1e9;
			return String.format(Locale.ROOT, "%.1f s and %d ticks", seconds, elapsed);
		}
	}
}
