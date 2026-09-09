package external.fixture.capability;

import external.fixture.protocol.FixtureConnection;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.capability.session.model.SessionState;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Set;

/**
 * Reuses the public Session capability through the fixture's own connection contract.
 */
public final class FixtureSessionProvider implements ProtocolPlayerCapabilityProvider<Session> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id("external.fixture.session").build();
	}

	@Override
	public @NotNull Set<String> supportedProtocolIds() {
		return Set.of("fixture");
	}

	@Override
	public @NotNull Class<Session> capability() {
		return Session.class;
	}

	@Override
	public @NotNull Session create(@NotNull ProtocolPlayerCapabilityContext context) {
		FixtureConnection connection = context.requireService(FixtureConnection.class);
		context.onDestroy(connection::disconnect);
		return new Session() {
			@Override
			public @NotNull SessionState state() {
				return SessionState.builder().connected(connection.connected()).build();
			}

			@Override
			public void connect() {
				connection.connect();
			}

			@Override
			public void disconnect() {
				connection.disconnect();
			}

			@Override
			public void rejoin() {
				disconnect();
				connect();
			}

			@Override
			public void connected(@NotNull Duration timeout) {
				if (!connection.connected())
					throw new IllegalStateException("Fixture is not connected");
			}

			@Override
			public void disconnected(@NotNull Duration timeout) {
				if (connection.connected())
					throw new IllegalStateException("Fixture is still connected");
			}

			@Override
			public @NotNull String kicked(@NotNull Duration timeout) {
				throw new UnsupportedOperationException("The fixture transport does not simulate kicks");
			}
		};
	}
}
