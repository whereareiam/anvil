package me.whereareiam.anvil.capability.messages.binding;

import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.channel.CapabilityChannel;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.messages.internal.McProtocolMessages;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for chat, commands, and received messages.
 */
public final class McProtocolMessagesProvider implements ProtocolPlayerCapabilityProvider<Messages> {
	static final String ID = "me.whereareiam.anvil.messages";

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
	public @NotNull Class<Messages> capability() {
		return Messages.class;
	}

	@Override
	public @NotNull Messages create(@NotNull ProtocolPlayerCapabilityContext context) {
		CapabilityChannel connection = context.channel();
		if (!connection.installedCapabilities().contains(ID))
			throw new CapabilityException("MCProtocol worker does not contain capability '"
					+ ID + "'");
		return new McProtocolMessages(new ChannelMessagesConnection(context));
	}
}
