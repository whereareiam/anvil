package me.whereareiam.anvil.capability.commands;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the command tree a player holds and the client's command check, over the player's typed
 * channel, for the libraries the commands family has a worker side for. Composition creates it only for players
 * whose worker installed the commands binding.
 */
public final class CommandsProvider implements ProtocolPlayerCapabilityProvider<Commands> {
	/**
	 * Capability ID shared by this provider and the worker extension of every library side.
	 */
	public static final String ID = "me.whereareiam.anvil.commands";

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
	public @NotNull Class<Commands> capability() {
		return Commands.class;
	}

	@Override
	public @NotNull Commands create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelCommands(context.channel());
	}
}
