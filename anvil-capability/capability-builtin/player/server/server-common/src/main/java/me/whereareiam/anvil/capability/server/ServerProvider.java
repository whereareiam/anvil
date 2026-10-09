package me.whereareiam.anvil.capability.server;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Host-side provider for agent-observed player identity and backend presence. It needs no native worker,
 * so it serves the players of every protocol library.
 */
public final class ServerProvider implements PlayerCapabilityProvider<Server> {
	static final String ID = "me.whereareiam.anvil.server";

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ID)
				.build();
	}

	@Override
	public @NotNull Class<Server> capability() {
		return Server.class;
	}

	@Override
	public @NotNull Server create(@NotNull PlayerCapabilityContext context) {
		return new ObservedServer(context.observation());
	}
}
