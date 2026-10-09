package me.whereareiam.anvil.capability.server;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.model.player.PlayerIdentity;
import me.whereareiam.anvil.api.player.PlayerObservation;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Server capability backed by the identities and routes that scenario process agents observe for one player.
 */
@RequiredArgsConstructor
final class ObservedServer implements Server {
	private final @NotNull PlayerObservation observation;

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

	@Override
	public void stayed(@NotNull String server, @NotNull Duration duration) {
		PlayerIdentity elsewhere;
		try {
			elsewhere = observation.await(identity -> !server.equals(identity.getRoute().getServer()), duration);
		} catch (IllegalStateException stayed) {
			if (server.equals(observation.identity().getRoute().getServer())) return;

			elsewhere = observation.identity();
		}

		throw new IllegalStateException("Player did not stay on server '" + server + "'; observed on "
				+ (elsewhere.getRoute().getServer() == null ? "no server" : "'" + elsewhere.getRoute().getServer() + "'"));
	}
}
