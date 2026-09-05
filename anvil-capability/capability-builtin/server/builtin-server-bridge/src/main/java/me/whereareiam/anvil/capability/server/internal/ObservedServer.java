package me.whereareiam.anvil.capability.server.internal;

import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.capability.server.Server;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Server capability backed by scenario process agent observations.
 */
final class ObservedServer implements Server {
	private final PlayerObservation observation;

	ObservedServer(PlayerObservation observation) {
		this.observation = observation;
	}

	@Override
	public @NotNull PlayerIdentity identity() {
		return observation.identity();
	}

	@Override
	public @NotNull PlayerIdentity joined(@NotNull String server, @NotNull Duration timeout) {
		return observation.await(
				identity -> server.equals(identity.getRoute().getServer()),
				timeout
		);
	}
}
