package me.whereareiam.anvil.capability.worlds.agent;

import me.whereareiam.anvil.capability.worlds.agent.model.AgentLogRequest;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldEvent;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldEvents;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The worlds one server process loaded and unloaded while it ran, in order, under an identity of their own, so a host
 * reading them tells a restarted process's log from the one it read before. A platform side records each event as its
 * platform reports it; the host reads what it has not read yet.
 */
public final class WorldEventLog {
	private final String identity;
	private final LongSupplier clock;
	private final List<AgentWorldEvent> events = new ArrayList<>();

	/**
	 * Starts an empty log with a new identity, timed on the system clock.
	 */
	public WorldEventLog() {
		this(UUID.randomUUID().toString(), System::currentTimeMillis);
	}

	WorldEventLog(@NotNull String identity, @NotNull LongSupplier clock) {
		this.identity = identity;
		this.clock = clock;
	}

	/**
	 * Records the server loading a world.
	 *
	 * @param world the key of the world's level
	 */
	public void loaded(@NotNull String world) {
		record(world, "LOADED");
	}

	/**
	 * Records the server unloading a world.
	 *
	 * @param world the key of the world's level
	 */
	public void unloaded(@NotNull String world) {
		record(world, "UNLOADED");
	}

	/**
	 * Returns the events a host has not read yet: those after the requested position of this log, or every event when
	 * the host last read another log.
	 *
	 * @param request what the host read last
	 * @return this log's identity and the events after the request
	 */
	public synchronized @NotNull AgentWorldEvents since(@NotNull AgentLogRequest request) {
		long after = Objects.equals(request.getLog(), identity) ? request.getAfter() : 0;
		int from = (int) Math.min(Math.max(after, 0), events.size());

		return AgentWorldEvents.builder()
				.log(identity)
				.events(new ArrayList<>(events.subList(from, events.size())))
				.build();
	}

	private synchronized void record(@NotNull String world, @NotNull String type) {
		events.add(AgentWorldEvent.builder()
				.position(events.size() + 1L)
				.world(world)
				.type(type)
				.at(clock.getAsLong())
				.build());
	}
}
