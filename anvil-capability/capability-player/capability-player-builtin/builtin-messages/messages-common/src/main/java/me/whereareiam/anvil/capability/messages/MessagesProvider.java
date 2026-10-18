package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.messages.channel.MessagesChannel;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for chat, commands and received messages over the player's typed channel, for the libraries
 * the messages family has a worker side for. Composition creates it only for players whose worker installed the
 * messages binding.
 */
public final class MessagesProvider implements ProtocolPlayerCapabilityProvider<Messages> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(MessagesChannel.ID)
				.build();
	}

	@Override
	public @NotNull Set<Class<?>> requiredProtocols() {
		return Set.of(JavaProtocol.class);
	}

	@Override
	public @NotNull Class<Messages> capability() {
		return Messages.class;
	}

	@Override
	public @NotNull Messages create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelMessages(context.channel());
	}
}
