package me.whereareiam.anvil.engine.player;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerRoute;
import me.whereareiam.anvil.agent.api.transport.AgentClient;
import me.whereareiam.anvil.agent.api.model.location.ProxyLocation;
import me.whereareiam.anvil.agent.api.model.location.ServerLocation;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.protocol.api.player.ProtocolPlayer;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Player-scoped identity observation assembled from every running platform agent.
 */
final class RunningPlayerObservation implements PlayerObservation {
	private final ProtocolPlayer player;
	private final Map<String, AgentClient> agents;
	private final Set<String> serverNames;

	RunningPlayerObservation(
			ProtocolPlayer player,
			Map<String, AgentClient> agents,
			Set<String> serverNames
	) {
		this.player = player;
		this.agents = Map.copyOf(agents);
		this.serverNames = serverNames;
	}

	@Override
	public @NotNull PlayerIdentity identity() {
		PlayerIdentity initial = player.identity();
		PlayerIdentity.PlayerIdentityBuilder result = initial.toBuilder();
		PlayerRoute.PlayerRouteBuilder route = initial.getRoute().toBuilder();
		for (Map.Entry<String, AgentClient> entry : agents.entrySet()) {
			var observed = entry.getValue().identity(player.name());
			if (observed.isEmpty())
				continue;
			var observedIdentity = observed.get();
			result.observedUsername(observedIdentity.getUsername())
					.observedUniqueId(observedIdentity.getUniqueId());
			if (observedIdentity.getLocation() instanceof ProxyLocation proxy)
				route.proxy(proxy.getProxy());
			if (observedIdentity.getLocation() instanceof ServerLocation server)
				route.server(serverNames.contains(entry.getKey()) ? entry.getKey() : server.getServer());
			if (observedIdentity.getLocation() instanceof ProxyLocation proxy
					&& proxy.getConnectedServer() != null)
				route.server(proxy.getConnectedServer());
		}
		return result.route(route.build()).build();
	}

	@Override
	public @NotNull PlayerIdentity await(
			@NotNull Predicate<PlayerIdentity> condition,
			@NotNull Duration timeout
	) {
		if (timeout.isNegative() || timeout.isZero())
			throw new IllegalArgumentException("timeout must be positive");
		long deadline = System.nanoTime() + timeout.toNanos();
		while (System.nanoTime() < deadline) {
			PlayerIdentity identity = identity();
			if (condition.test(identity))
				return identity;
			try {
				Thread.sleep(25);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while observing player '" + player.name() + "'",
						exception);
			}
		}
		throw new IllegalStateException("Player '" + player.name() + "' did not satisfy the observation condition within "
				+ timeout + "; identity=" + identity());
	}
}
