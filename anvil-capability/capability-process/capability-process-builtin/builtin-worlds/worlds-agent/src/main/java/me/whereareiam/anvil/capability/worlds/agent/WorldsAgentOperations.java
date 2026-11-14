package me.whereareiam.anvil.capability.worlds.agent;

import me.whereareiam.anvil.agent.server.api.PlatformAgent;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationProvider;
import me.whereareiam.anvil.agent.server.api.operation.AgentOperationRegistry;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentPlacements;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldList;
import me.whereareiam.anvil.capability.worlds.agent.model.AgentWorldOutcome;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Answers the worlds capability's operations on one platform: each platform side extends it with the
 * {@link ServerWorlds} of its platform, which it opens once the agent starts, together with the logs it records the
 * platform's world changes and world events in. Every operation that reads or changes a world runs on the platform's own thread. A
 * creation or unloading the server refuses, or the platform cannot do, is answered as such instead of as a failure.
 */
public abstract class WorldsAgentOperations implements AgentOperationProvider {
	@Override
	public final @NotNull String id() {
		return WorldsOperations.NAMESPACE;
	}

	@Override
	public final void install(@NotNull PlatformAgent platform, @NotNull AgentOperationRegistry registry) {
		WorldChangeLog changes = new WorldChangeLog();
		WorldEventLog events = new WorldEventLog();
		ServerWorlds worlds = open(platform, changes, events);

		registry.register(WorldsOperations.WORLDS, (agent, ignored) ->
				agent.call(() -> AgentWorldList.builder().worlds(worlds.worlds()).build()));
		registry.register(WorldsOperations.PLACEMENTS, (agent, ignored) ->
				agent.call(() -> AgentPlacements.builder().placements(worlds.placements()).build()));
		registry.register(WorldsOperations.SEND, (agent, transfer) ->
				agent.call(() -> worlds.teleport(transfer.getUniqueId(), transfer.getLocation())));
		registry.register(WorldsOperations.CHANGES, (agent, request) -> changes.since(request));
		registry.register(WorldsOperations.CREATE, (agent, creation) ->
				agent.call(() -> outcome(() -> AgentWorldOutcome.builder().world(worlds.create(creation)).build())));
		registry.register(WorldsOperations.UNLOAD, (agent, key) -> agent.call(() -> outcome(() -> {
			worlds.unload(key);
			return AgentWorldOutcome.builder().build();
		})));
		registry.register(WorldsOperations.DELETE, (agent, key) -> agent.call(() -> outcome(() -> {
			worlds.delete(key);
			return AgentWorldOutcome.builder().build();
		})));
		registry.register(WorldsOperations.EVENTS, (agent, request) -> events.since(request));
	}

	/**
	 * Opens the platform's worlds and starts recording its players' world changes and the worlds it loads and unloads.
	 *
	 * @param platform the platform agent, whose services reach the platform's API
	 * @param changes the log to record each world change the platform reports in
	 * @param events the log to record each world the platform loads or unloads in
	 * @return the platform's worlds
	 */
	protected abstract @NotNull ServerWorlds open(
			@NotNull PlatformAgent platform,
			@NotNull WorldChangeLog changes,
			@NotNull WorldEventLog events
	);

	private static @NotNull AgentWorldOutcome outcome(@NotNull Supplier<AgentWorldOutcome> request) {
		try {
			return request.get();
		} catch (UnsupportedOperationException exception) {
			return AgentWorldOutcome.builder().unsupported(exception.getMessage()).build();
		} catch (IllegalStateException exception) {
			return AgentWorldOutcome.builder().refusal(exception.getMessage()).build();
		}
	}
}
