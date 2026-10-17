package me.whereareiam.anvil.agent.server.api.event;

import me.whereareiam.anvil.agent.api.model.AgentProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Where a platform agent reports players joining and leaving, once, for every capability and test that asks the
 * agent. The endpoint hands it to the agent at startup through {@code AgentServer.playerEvents()}; the agent calls it
 * from the platform's own events.
 *
 * <p>One rule names where the player joined or left: an empty {@code server} means this process itself, a value
 * means a backend behind this process, which only a proxy has, named as the proxy names it. A server reports the
 * player joining and leaving it with an empty {@code server}. A proxy reports the player joining it, once it has
 * logged in and before any backend, and leaving it when the player disconnects, with an empty {@code server}, and
 * every backend it connects the player to and away from with that backend's name, leaving the previous backend
 * before joining the next.</p>
 *
 * <pre>{@code
 * AgentServer agent = AgentServerProvider.discover().start(this, logger);
 * PlayerEvents events = agent.playerEvents();
 * // in the platform's join listener:
 * events.joined(AgentProfile.builder().username(player.getName()).uniqueId(player.getUniqueId()).build(), null);
 * }</pre>
 */
public interface PlayerEvents {
	/**
	 * Records that a player joined this process, or on a proxy, a backend behind it.
	 *
	 * @param profile the player as this platform reports it
	 * @param server the backend the proxy connected the player to, or null for this process itself
	 */
	void joined(@NotNull AgentProfile profile, @Nullable String server);

	/**
	 * Records that a player left this process, or on a proxy, a backend behind it that the player moved away from or
	 * disconnected from.
	 *
	 * @param profile the player as this platform reports it
	 * @param server the backend the player left, or null for this process itself
	 */
	void left(@NotNull AgentProfile profile, @Nullable String server);
}
