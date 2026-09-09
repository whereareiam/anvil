package me.whereareiam.anvil.capability.interaction.binding;

import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.interaction.Interaction;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for item, block, and entity interaction.
 */
public final class McProtocolInteractionProvider implements ProtocolPlayerCapabilityProvider<Interaction> {
	static final String ID = "me.whereareiam.anvil.interaction";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ID)
				.build();
	}

	@Override
	public @NotNull Set<String> supportedProtocolIds() {
		return Set.of("mcprotocol");
	}

	@Override
	public @NotNull Class<Interaction> capability() {
		return Interaction.class;
	}

	@Override
	public @NotNull Interaction create(@NotNull ProtocolPlayerCapabilityContext context) {
		CapabilityChannel connection = context.channel();
		if (!connection.installedCapabilities().contains(ID))
			throw new CapabilityException("MCProtocol worker does not contain capability '"
					+ ID + "'");
		return new McProtocolInteraction(connection);
	}
}
