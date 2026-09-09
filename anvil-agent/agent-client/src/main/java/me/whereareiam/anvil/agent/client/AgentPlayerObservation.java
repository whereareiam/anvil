package me.whereareiam.anvil.agent.client;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import me.whereareiam.anvil.agent.api.model.location.ProxyLocation;
import me.whereareiam.anvil.agent.api.model.location.ServerLocation;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerRoute;
import me.whereareiam.anvil.api.player.PlayerObservation;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Player-scoped identity observation assembled from every running platform agent.
 */
@RequiredArgsConstructor
public final class AgentPlayerObservation implements PlayerObservation {
	private final @NotNull String playerName;
	private final @NotNull Supplier<PlayerIdentity> identity;
	private final @NotNull AgentDirectory agents;
	private final @NotNull Set<String> serverNames;

	@Override
	public @NotNull PlayerIdentity identity() {
		PlayerIdentity initial = identity.get();
		PlayerIdentity.PlayerIdentityBuilder result = initial.toBuilder();
		PlayerRoute.PlayerRouteBuilder route = initial.getRoute().toBuilder();
		for (Map.Entry<String, AgentClient> entry : agents.agents().entrySet()) {
			var observed = entry.getValue().identity(playerName);
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
			if (condition.test(identity)) return identity;

			try {
				Thread.sleep(25);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while observing player '" + playerName + "'",
						exception);
			}
		}

		throw new IllegalStateException("Player '" + playerName + "' did not satisfy the observation condition within "
				+ timeout + "; identity=" + identity());
	}
}
