package me.whereareiam.anvil.capability.worlds.agent;

import me.whereareiam.anvil.capability.worlds.agent.model.AgentLogRequest;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChange;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldChanges;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * The world changes one server process reported, in order, under an identity of their own, so a host reading them
 * tells a restarted process's log from the one it read before. A platform side records each change as its platform
 * reports it; the host reads what it has not read yet.
 */
public final class WorldChangeLog {
	private final String identity;
	private final LongSupplier clock;
	private final List<AgentWorldChange> changes = new ArrayList<>();

	/**
	 * Starts an empty log with a new identity, timed on the system clock.
	 */
	public WorldChangeLog() {
		this(UUID.randomUUID().toString(), System::currentTimeMillis);
	}

	WorldChangeLog(@NotNull String identity, @NotNull LongSupplier clock) {
		this.identity = identity;
		this.clock = clock;
	}

	/**
	 * Records a player moving from one world to another.
	 *
	 * @param uniqueId the UUID the server reports for the player
	 * @param username the player's name as the server reports it
	 * @param from the key of the world the player left
	 * @param to the key of the world the player is in afterwards
	 */
	public synchronized void record(@NotNull UUID uniqueId, @NotNull String username, @NotNull String from, @NotNull String to) {
		changes.add(AgentWorldChange.builder()
				.position(changes.size() + 1L)
				.uniqueId(uniqueId)
				.username(username)
				.from(from)
				.to(to)
				.at(clock.getAsLong())
				.build());
	}

	/**
	 * Returns the changes a host has not read yet: those after the requested position of this log, or every change
	 * when the host last read another log.
	 *
	 * @param request what the host read last
	 * @return this log's identity and the changes after the request
	 */
	public synchronized @NotNull AgentWorldChanges since(@NotNull AgentLogRequest request) {
		long after = Objects.equals(request.getLog(), identity) ? request.getAfter() : 0;
		int from = (int) Math.min(Math.max(after, 0), changes.size());

		return AgentWorldChanges.builder()
				.log(identity)
				.changes(new ArrayList<>(changes.subList(from, changes.size())))
				.build();
	}
}
