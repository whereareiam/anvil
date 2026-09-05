package me.whereareiam.anvil.capability.session.internal;

import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolPlayerConnection;
import me.whereareiam.anvil.capability.api.CapabilityException;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.session.Session;
import org.jetbrains.annotations.NotNull;

/**
 * Host-side provider for the standard session capability.
 */
public final class McProtocolSessionProvider implements PlayerCapabilityProvider<Session> {
	static final String ID = "me.whereareiam.anvil.session";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder().id(ID).supportedProtocolId("mcprotocol").build();
	}

	@Override
	public @NotNull Class<Session> capability() {
		return Session.class;
	}

	@Override
	public @NotNull Session create(@NotNull PlayerCapabilityContext context) {
		ProtocolPlayerConnection connection = context.requireService(ProtocolPlayerConnection.class);
		if (!connection.workerCapabilities().contains(ID))
			throw new CapabilityException("MCProtocol worker does not contain capability '"
					+ ID + "'");
		return new McProtocolSession(connection);
	}
}
