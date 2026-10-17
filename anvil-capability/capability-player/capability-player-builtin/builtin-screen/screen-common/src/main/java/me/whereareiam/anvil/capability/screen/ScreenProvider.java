package me.whereareiam.anvil.capability.screen;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the titles, action bars and boss bars a player is shown, over the player's typed channel,
 * for the libraries the screen family has a worker side for. Composition creates it only for players whose worker
 * installed the screen binding.
 */
public final class ScreenProvider implements ProtocolPlayerCapabilityProvider<Screen> {
	/**
	 * Capability ID shared by this provider and the worker extension of every library side.
	 */
	public static final String ID = "me.whereareiam.anvil.screen";

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
	public @NotNull Class<Screen> capability() {
		return Screen.class;
	}

	@Override
	public @NotNull Screen create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelScreen(context.channel());
	}
}
