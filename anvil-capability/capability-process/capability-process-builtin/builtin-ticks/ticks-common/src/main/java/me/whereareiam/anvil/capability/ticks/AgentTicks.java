package me.whereareiam.anvil.capability.ticks;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentOperation;
import me.whereareiam.anvil.api.model.Condition;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.process.RequestChannel;
import me.whereareiam.anvil.capability.ticks.agent.TicksOperations;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickCount;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickWait;
import me.whereareiam.anvil.capability.ticks.type.TickPace;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/**
 * Ticks capability of one server, over the operations its ticks agent extension answers. A wait asks the agent for
 * the server's next tick, which the agent answers as soon as the server completes one, so a wait follows the server's
 * ticks without sleeping; it asks for at most a second at a time, so a frozen server still lets it give up in time.
 *
 * <p>How fast the server runs is up to {@link OpenPaces}, which a wait does not touch: it counts whatever ticks the
 * server runs.</p>
 */
@RequiredArgsConstructor
final class AgentTicks implements Ticks {
	private static final Duration LONGEST_WAIT = Duration.ofSeconds(1);
	private static final ChannelOperation<Void, AgentTickCount> COUNT = channel(TicksOperations.COUNT);
	private static final ChannelOperation<AgentTickWait, AgentTickCount> NEXT = channel(TicksOperations.NEXT);

	private final @NotNull String process;
	private final @NotNull RequestChannel channel;
	private final @NotNull OpenPaces paces;

	@Override
	public long tick() {
		return count().tick();
	}

	@Override
	public void await(int ticks, @NotNull Duration timeout) {
		if (ticks <= 0) throw new IllegalArgumentException("ticks must be positive");
		Progress progress = new Progress(count(), timeout);
		if (progress.reach(ticks)) return;

		throw new IllegalStateException("'" + process + "' ran " + progress.elapsed + " of " + ticks + " ticks within " + timeout);
	}

	@Override
	public <T> T until(@NotNull Condition<T> condition, @NotNull Duration timeout) {
		Progress progress = new Progress(count(), timeout);
		while (true) {
			T seen = condition.observe();
			if (condition.holds(seen)) return seen;
			if (!progress.step())
				throw new IllegalStateException("Gave up on '" + process + "' after " + progress + " waiting until "
						+ condition.description() + "; last seen: " + seen);
		}
	}

	@Override
	public @NotNull Pacing pace(@NotNull TickPace pace) {
		return paces.open(pace);
	}

	private @NotNull AgentTickCount count() {
		return Objects.requireNonNull(channel.request(COUNT, null));
	}

	private static <Q, R> @NotNull ChannelOperation<Q, R> channel(@NotNull AgentOperation<Q, R> operation) {
		return new ChannelOperation<>(operation.name(), operation.requestType(), operation.responseType());
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
			if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");

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
					.run(seen.run())
					.after(seen.tick())
					.waitMillis(wait)
					.build()));
			elapsed += next.run().equals(seen.run()) ? next.tick() - seen.tick() : next.tick();
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
