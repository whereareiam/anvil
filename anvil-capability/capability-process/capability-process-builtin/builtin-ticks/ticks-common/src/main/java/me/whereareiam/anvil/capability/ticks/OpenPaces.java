package me.whereareiam.anvil.capability.ticks;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.capability.api.exception.ChannelUnavailableException;
import me.whereareiam.anvil.capability.api.model.channel.ChannelOperation;
import me.whereareiam.anvil.capability.api.process.RequestChannel;
import me.whereareiam.anvil.capability.ticks.agent.TicksOperations;
import me.whereareiam.anvil.capability.ticks.agent.model.AgentTickPace;
import me.whereareiam.anvil.capability.ticks.type.TickPace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * The paces a scenario has set on one server and not closed yet, innermost first. Each remembers the pace the server
 * ran at before it, which closing it gives back. The scenario's end closes those left open, and a restarted server,
 * which starts at its own pace, is given the innermost open pace again.
 */
@RequiredArgsConstructor
final class OpenPaces {
	private static final ChannelOperation<AgentTickPace, AgentTickPace> PACE = new ChannelOperation<>(
			TicksOperations.PACE.name(),
			TicksOperations.PACE.requestType(),
			TicksOperations.PACE.responseType()
	);

	private final @NotNull String process;
	private final @NotNull RequestChannel channel;
	private final Deque<Opened> open = new ArrayDeque<>();

	/**
	 * Has the server run at a pace until the returned handle is closed.
	 */
	synchronized @NotNull Pacing open(@NotNull TickPace pace) {
		AgentTickPace wire = wire(pace);
		Opened opened = new Opened(pace, wire, set(wire));
		open.push(opened);

		return opened;
	}

	/**
	 * Closes the paces the finished scenario left open, innermost first. A server that has stopped runs at no pace.
	 *
	 * @throws RuntimeException the first failure to give a pace back, with later ones suppressed
	 */
	synchronized void closeAll() {
		RuntimeException failure = null;
		while (!open.isEmpty()) {
			try {
				open.peek().close();
			} catch (ChannelUnavailableException stopped) {
				// The server stopped, and its pace with it.
			} catch (RuntimeException closing) {
				if (failure == null) failure = closing;
				else failure.addSuppressed(closing);
			}
		}
		if (failure != null) throw failure;
	}

	/**
	 * Gives a restarted server, which starts at its own pace, the innermost pace still open.
	 */
	synchronized void restarted() {
		Opened innermost = open.peek();
		if (innermost != null) set(innermost.wire);
	}

	private @NotNull AgentTickPace set(@NotNull AgentTickPace pace) {
		return Objects.requireNonNull(channel.request(PACE, pace));
	}

	private static @NotNull AgentTickPace wire(@NotNull TickPace pace) {
		return switch (pace) {
			case TickPace.Normal normal -> AgentTickPace.builder().rate(TickPace.NORMAL_RATE).sprinting(false).build();
			case TickPace.Fastest fastest -> AgentTickPace.builder().rate(TickPace.NORMAL_RATE).sprinting(true).build();
			case TickPace.Rate rate -> AgentTickPace.builder().rate(rate.ticksPerSecond()).sprinting(false).build();
		};
	}

	/**
	 * One open pace, with the pace the server ran at before it.
	 */
	@RequiredArgsConstructor
	private final class Opened implements Pacing {
		private final @NotNull TickPace pace;
		private final @NotNull AgentTickPace wire;
		private final @NotNull AgentTickPace previous;

		@Override
		public @NotNull TickPace pace() {
			return pace;
		}

		@Override
		public void close() {
			synchronized (OpenPaces.this) {
				if (!open.contains(this)) return;

				@Nullable Opened innermost = open.peek();
				if (innermost != this)
					throw new IllegalStateException("The pace " + pace + " of '" + process + "' cannot close while the pace "
							+ Objects.requireNonNull(innermost).pace + " set after it is still open");

				open.pop();
				set(previous);
			}
		}
	}
}
