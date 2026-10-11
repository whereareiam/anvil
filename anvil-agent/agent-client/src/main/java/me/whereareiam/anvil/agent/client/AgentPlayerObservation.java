package me.whereareiam.anvil.agent.client;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.agent.api.model.AgentIdentity;
import me.whereareiam.anvil.agent.api.model.location.ProxyLocation;
import me.whereareiam.anvil.agent.api.model.location.ServerLocation;
import me.whereareiam.anvil.agent.client.api.AgentClient;
import me.whereareiam.anvil.agent.client.api.AgentDirectory;
import me.whereareiam.anvil.agent.client.api.exception.AgentUnavailableException;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.model.player.PlayerProfile;
import me.whereareiam.anvil.api.model.player.PlayerRoute;
import me.whereareiam.anvil.api.player.PlayerObservation;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Player-scoped identity observation that follows one player's connection: the agent of the process the
 * player connects to reports it first, and a proxy's report names the backend whose agent is asked next.
 * Each agent is asked for the UUID the player was granted at login, which a proxy forwards to its backends, so
 * players sharing a username, or a username that differs only in letter case, on this or another process are
 * never mistaken for this one, and a player a proxy renamed is still found.
 *
 * <p>Routes name scenario processes: the proxy is the process the player connected to, whatever the agent
 * calls its platform. The backend's report wins over the proxy's for the observed profile, because the backend
 * holds the identity the proxy forwarded. Before the player is granted a profile no agent is asked.</p>
 */
@RequiredArgsConstructor
public final class AgentPlayerObservation implements PlayerObservation {
	private final @NotNull String playerName;
	private final @NotNull String connectedTo;
	private final @NotNull Supplier<PlayerIdentity> identity;
	private final @NotNull AgentDirectory agents;
	private final @NotNull Set<String> serverNames;

	@Override
	public @NotNull PlayerIdentity identity() {
		PlayerIdentity initial = identity.get();
		PlayerProfile granted = initial.getGranted();
		if (granted == null) return initial;

		Optional<AgentIdentity> entry = observe(connectedTo, granted.getUniqueId());
		if (entry.isEmpty()) return initial;

		PlayerIdentity.PlayerIdentityBuilder result = initial.toBuilder().observed(profile(entry.get()));
		PlayerRoute.PlayerRouteBuilder route = initial.getRoute().toBuilder();
		if (entry.get().getLocation() instanceof ServerLocation server)
			route.server(serverNames.contains(connectedTo) ? connectedTo : server.getServer());
		if (entry.get().getLocation() instanceof ProxyLocation proxy) {
			route.proxy(connectedTo);
			route.server(proxy.getConnectedServer());
			if (proxy.getConnectedServer() != null)
				observe(proxy.getConnectedServer(), granted.getUniqueId()).ifPresent(backend -> result.observed(profile(backend)));
		}

		return result.route(route.build()).build();
	}

	private Optional<AgentIdentity> observe(String process, UUID uniqueId) {
		AgentClient agent = agents.agents().get(process);
		if (agent == null || !agent.available()) return Optional.empty();

		try {
			return agent.identity(uniqueId);
		} catch (AgentUnavailableException exception) {
			return Optional.empty();
		}
	}

	private static PlayerProfile profile(AgentIdentity observed) {
		return new PlayerProfile(observed.getUsername(), observed.getUniqueId());
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
