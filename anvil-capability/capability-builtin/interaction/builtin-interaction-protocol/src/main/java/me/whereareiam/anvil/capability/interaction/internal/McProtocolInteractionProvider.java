package me.whereareiam.anvil.capability.interaction.internal;

import me.whereareiam.anvil.protocol.adapter.api.player.ProtocolPlayerConnection;
import me.whereareiam.anvil.capability.api.CapabilityException;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.interaction.Interaction;
import me.whereareiam.anvil.capability.session.Session;
import org.jetbrains.annotations.NotNull;

/**
 * Host-side provider for item, block, and entity interaction.
 */
public final class McProtocolInteractionProvider implements PlayerCapabilityProvider<Interaction> {
	static final String ID = "me.whereareiam.anvil.interaction";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ID)
				.supportedProtocolId("mcprotocol")
				.requiredCapability(Session.class)
				.build();
	}

	@Override
	public @NotNull Class<Interaction> capability() {
		return Interaction.class;
	}

	@Override
	public @NotNull Interaction create(@NotNull PlayerCapabilityContext context) {
		context.requireCapability(Session.class);
		ProtocolPlayerConnection connection = context.requireService(ProtocolPlayerConnection.class);
		if (!connection.workerCapabilities().contains(ID))
			throw new CapabilityException("MCProtocol worker does not contain capability '"
					+ ID + "'");
		return new McProtocolInteraction(connection);
	}
}
