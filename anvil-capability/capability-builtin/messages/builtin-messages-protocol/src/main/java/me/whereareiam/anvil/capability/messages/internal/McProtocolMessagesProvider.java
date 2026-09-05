package me.whereareiam.anvil.capability.messages.internal;

import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolPlayerConnection;
import me.whereareiam.anvil.capability.api.CapabilityException;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.session.Session;
import org.jetbrains.annotations.NotNull;

/**
 * Host-side provider for chat, commands, and received messages.
 */
public final class McProtocolMessagesProvider implements PlayerCapabilityProvider<Messages> {
	static final String ID = "me.whereareiam.anvil.messages";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ID)
				.supportedProtocolId("mcprotocol")
				.requiredCapability(Session.class)
				.build();
	}

	@Override
	public @NotNull Class<Messages> capability() {
		return Messages.class;
	}

	@Override
	public @NotNull Messages create(@NotNull PlayerCapabilityContext context) {
		context.requireCapability(Session.class);
		ProtocolPlayerConnection connection = context.requireService(ProtocolPlayerConnection.class);
		if (!connection.workerCapabilities().contains(ID))
			throw new CapabilityException("MCProtocol worker does not contain capability '"
					+ ID + "'");
		return new McProtocolMessages(connection);
	}
}
