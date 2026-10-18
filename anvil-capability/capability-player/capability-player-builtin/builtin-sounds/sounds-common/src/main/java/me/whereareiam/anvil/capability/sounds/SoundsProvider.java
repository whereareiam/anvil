package me.whereareiam.anvil.capability.sounds;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.sounds.channel.SoundsChannel;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the sounds a player hears, over the player's typed channel, for the libraries the sounds
 * family has a worker side for. Composition creates it only for players whose worker installed the sounds extension.
 */
public final class SoundsProvider implements ProtocolPlayerCapabilityProvider<Sounds> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(SoundsChannel.ID)
				.build();
	}

	@Override
	public @NotNull Set<Class<?>> requiredProtocols() {
		return Set.of(JavaProtocol.class);
	}

	@Override
	public @NotNull Class<Sounds> capability() {
		return Sounds.class;
	}

	@Override
	public @NotNull Sounds create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelSounds(context.channel());
	}
}
