package me.whereareiam.anvil.capability.messages;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for chat, commands and received messages over the player's typed channel, for the libraries
 * the messages family has a worker side for. Composition creates it only for players whose worker installed the
 * messages binding.
 */
public final class MessagesProvider implements ProtocolPlayerCapabilityProvider<Messages> {
	/**
	 * Capability ID shared by this provider and the worker extension of every library side.
	 */
	public static final String ID = "me.whereareiam.anvil.messages";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ID)
				.build();
	}

	@Override
	public @NotNull Set<String> supportedLibraries() {
		return Set.of("mcprotocol");
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
