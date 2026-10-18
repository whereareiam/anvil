package me.whereareiam.anvil.capability.screen;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.screen.channel.ScreenChannel;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the titles, action bars and boss bars a player is shown, over the player's typed channel,
 * for the libraries the screen family has a worker side for. Composition creates it only for players whose worker
 * installed the screen extension.
 */
public final class ScreenProvider implements ProtocolPlayerCapabilityProvider<Screen> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(ScreenChannel.ID)
				.build();
	}

	@Override
	public @NotNull Set<Class<?>> requiredProtocols() {
		return Set.of(JavaProtocol.class);
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
