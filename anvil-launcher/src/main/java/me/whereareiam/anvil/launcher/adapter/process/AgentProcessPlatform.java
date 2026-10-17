package me.whereareiam.anvil.launcher.adapter.process;

import me.whereareiam.anvil.agent.api.model.AgentPlayerEvent;
import me.whereareiam.anvil.agent.api.model.AgentOperations;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandRequest;
import me.whereareiam.anvil.agent.api.model.transport.command.AgentCommandResponse;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsRequest;
import me.whereareiam.anvil.agent.api.model.transport.event.AgentPlayerEventsResponse;
import me.whereareiam.anvil.agent.api.type.AgentPlayerEventType;
import me.whereareiam.anvil.agent.client.api.connection.AgentConnection;
import me.whereareiam.anvil.agent.client.api.exception.AgentUnavailableException;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.capability.api.exception.ChannelUnavailableException;
import me.whereareiam.anvil.capability.api.model.platform.PlatformEvent;
import me.whereareiam.anvil.capability.api.platform.ProcessPlatform;
import me.whereareiam.anvil.capability.api.type.PlatformEventType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * What one process's platform agent provides to every capability, over the agent's built-in operations. It keeps the
 * process's events, reading only what is new on each call, and numbers them itself, so positions keep increasing when
 * the process restarts and its agent reports a new generation. A restart ends every stay the earlier generation left
 * open with a leave before anything the new generation reported. Their times never decrease, so sorting the events
 * of several processes by time keeps each process's own order.
 */
final class AgentProcessPlatform implements ProcessPlatform {
	private final String process;
	private final Supplier<Optional<? extends AgentConnection>> connection;
	private final Instant since;
	private final List<PlatformEvent> events = new ArrayList<>();
	private final Map<String, PlatformEvent> open = new LinkedHashMap<>();
	private @Nullable String generation;
	private long read;
	private long position;

	/**
	 * Creates the platform of one process.
	 *
	 * @param process scenario process name
	 * @param connection finds the process's agent connection, which a restart replaces
	 * @param since the moment the scenario began; a process that already ran then, such as one kept for the
	 *              engine's lifetime, reported what happened before, which this platform leaves out
	 */
	AgentProcessPlatform(@NotNull String process, @NotNull Supplier<Optional<? extends AgentConnection>> connection, @NotNull Instant since) {
		this.process = process;
		this.connection = connection;
		this.since = since;
	}

	@Override
	public boolean execute(@NotNull String command) {
		AgentCommandResponse response = request(AgentOperations.COMMAND.getName(), AgentCommandRequest.builder().command(command).build(),
				AgentCommandResponse.class);
		if (response == null) throw new IllegalStateException("Agent of '" + process + "' answered no command result");

		return response.isAccepted();
	}

	@Override
	public synchronized @NotNull List<PlatformEvent> events() {
		AgentPlayerEventsResponse response = fetch(read);
		if (response != null && generation != null && !response.getGeneration().equals(generation)) {
			read = 0;
			response = fetch(0);
			if (response == null) return List.copyOf(events);

			restarted(ended(response));
		}
		if (response == null) return List.copyOf(events);

		generation = response.getGeneration();
		boolean proxy = response.isProxy();
		response.getEvents().forEach(event -> keep(event, proxy));

		return List.copyOf(events);
	}

	private void keep(AgentPlayerEvent event, boolean proxy) {
		read = Math.max(read, event.getPosition());
		if (event.getAt() < since.toEpochMilli()) return;

		PlatformEvent kept = PlatformEvent.builder()
				.process(process)
				.proxy(proxy)
				.position(++position)
				.at(notBeforeLast(Instant.ofEpochMilli(event.getAt())))
				.type(event.getType() == AgentPlayerEventType.JOINED ? PlatformEventType.JOINED : PlatformEventType.LEFT)
				.profile(new PlayerProfile(event.getProfile().getUsername(), event.getProfile().getUniqueId()))
				.server(event.getServer())
				.build();
		events.add(kept);

		String stay = event.getProfile().getUniqueId() + "@" + event.getServer();
		if (kept.getType() == PlatformEventType.JOINED) open.put(stay, kept);
		else open.remove(stay);
	}

	/**
	 * Returns when the earlier generation ended at the latest: before the new one reported anything, or now.
	 */
	private Instant ended(AgentPlayerEventsResponse generation) {
		Instant now = Instant.now();
		Instant ended = generation.getEvents().stream()
				.findFirst()
				.map(first -> Instant.ofEpochMilli(first.getAt()))
				.filter(first -> first.isBefore(now))
				.orElse(now);

		return notBeforeLast(ended);
	}

	/**
	 * Keeps the process's events in the order it reported them when they are sorted by time, also when its clock or a
	 * restart would put one before the previous.
	 */
	private Instant notBeforeLast(Instant at) {
		if (events.isEmpty()) return at;

		Instant last = events.getLast().getAt();
		return at.isBefore(last) ? last : at;
	}

	private void restarted(Instant ended) {
		open.values().forEach(joined -> events.add(PlatformEvent.builder()
				.process(process)
				.proxy(joined.isProxy())
				.position(++position)
				.at(ended)
				.type(PlatformEventType.LEFT)
				.profile(joined.getProfile())
				.server(joined.getServer())
				.build()));
		open.clear();
	}

	/**
	 * Reads the events after a position, or nothing while the process's agent cannot be reached.
	 */
	private @Nullable AgentPlayerEventsResponse fetch(long after) {
		try {
			return request(AgentOperations.PLAYER_EVENTS.getName(), AgentPlayerEventsRequest.builder().after(after).build(), AgentPlayerEventsResponse.class);
		} catch (ChannelUnavailableException unavailable) {
			return null;
		}
	}

	private <T> @Nullable T request(String operation, Object arguments, Class<T> responseType) {
		AgentConnection agent = connection.get().orElseThrow(() -> new ChannelUnavailableException(
				"Process '" + process + "' has no agent", new IllegalStateException("No agent registered for '" + process + "'")));
		try {
			return agent.request(operation, arguments, responseType);
		} catch (AgentUnavailableException unavailable) {
			throw new ChannelUnavailableException("The agent of process '" + process + "' is unavailable: "
					+ unavailable.getMessage(), unavailable);
		}
	}
}
