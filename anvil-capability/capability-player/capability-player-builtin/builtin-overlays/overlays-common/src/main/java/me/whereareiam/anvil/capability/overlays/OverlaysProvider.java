package me.whereareiam.anvil.capability.overlays;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.overlays.channel.OverlaysChannel;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.edition.java.protocol.JavaProtocol;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the titles, action bars and boss bars a player is shown, over the player's typed channel,
 * for the libraries the overlays family has a worker side for. Composition creates it only for players whose worker
 * installed the overlays extension.
 */
public final class OverlaysProvider implements ProtocolPlayerCapabilityProvider<Overlays> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(OverlaysChannel.ID)
				.build();
	}

	@Override
	public @NotNull Set<Class<?>> requiredProtocols() {
		return Set.of(JavaProtocol.class);
	}

	@Override
	public @NotNull Class<Overlays> capability() {
		return Overlays.class;
	}

	@Override
	public @NotNull Overlays create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelOverlays(context.channel());
	}
}
