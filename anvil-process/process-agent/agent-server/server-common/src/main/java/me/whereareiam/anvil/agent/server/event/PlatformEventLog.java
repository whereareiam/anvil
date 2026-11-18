package me.whereareiam.anvil.agent.server.event;

import me.whereareiam.anvil.agent.api.model.AgentPlayerEvent;
import me.whereareiam.anvil.agent.api.model.AgentProfile;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsResponse;
import me.whereareiam.anvil.agent.api.type.AgentPlayerEventType;
import me.whereareiam.anvil.agent.server.api.event.PlayerEvents;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The joins and leaves one endpoint records, for every player, in the order the platform reports them. The oldest
 * events are dropped once it holds {@value #LIMIT}. Each endpoint belongs to one process generation, so a restarted
 * process reports a new generation, whose positions start again.
 */
public final class PlatformEventLog implements PlayerEvents {
	/**
	 * Most events the log keeps.
	 */
	public static final int LIMIT = 10_000;

	private final String generation = UUID.randomUUID().toString();
	private final boolean proxy;
	private final Deque<AgentPlayerEvent> events = new ArrayDeque<>();
	private long position;

	/**
	 * Creates an empty log for one process generation.
	 *
	 * @param proxy whether the process is a proxy
	 */
	public PlatformEventLog(boolean proxy) {
		this.proxy = proxy;
	}

	@Override
	public void joined(@NotNull AgentProfile profile, @Nullable String server) {
		record(AgentPlayerEventType.JOINED, profile, server);
	}

	@Override
	public void left(@NotNull AgentProfile profile, @Nullable String server) {
		record(AgentPlayerEventType.LEFT, profile, server);
	}

	/**
	 * Answers the events recorded after a position.
	 *
	 * @param after position of the last event the caller has, or zero for every kept event
	 * @return the events after it, oldest first
	 */
	public synchronized @NotNull AgentPlayerEventsResponse after(long after) {
		List<AgentPlayerEvent> later = events.stream()
				.filter(event -> event.getPosition() > after)
				.collect(Collectors.toList());
		boolean truncated = !events.isEmpty() && after < events.getFirst().getPosition() - 1;

		return AgentPlayerEventsResponse.builder().generation(generation).proxy(proxy).truncated(truncated).events(later).build();
	}

	private synchronized void record(AgentPlayerEventType type, AgentProfile profile, @Nullable String server) {
		events.addLast(AgentPlayerEvent.builder()
				.position(++position)
				.at(System.currentTimeMillis())
				.type(type)
				.profile(profile)
				.server(server)
				.build());
		while (events.size() > LIMIT) events.removeFirst();
	}
}
