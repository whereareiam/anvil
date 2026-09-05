package me.whereareiam.anvil.capability.server.internal;

import me.whereareiam.anvil.api.player.PlayerObservation;
import me.whereareiam.anvil.capability.api.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.api.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.server.Server;
import org.jetbrains.annotations.NotNull;

/**
 * Host-side provider for agent-observed player identity and backend presence.
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
		return new ObservedServer(context.requireService(PlayerObservation.class));
	}
}
