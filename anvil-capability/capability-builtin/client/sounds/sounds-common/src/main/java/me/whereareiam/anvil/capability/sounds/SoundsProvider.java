package me.whereareiam.anvil.capability.sounds;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the sounds a player hears, over the player's typed channel, for the libraries the sounds
 * family has a worker side for. Composition creates it only for players whose worker installed the sounds binding.
 */
public final class SoundsProvider implements ProtocolPlayerCapabilityProvider<Sounds> {
	/**
	 * Capability ID shared by this provider and the worker extension of every library side.
	 */
	public static final String ID = "me.whereareiam.anvil.sounds";

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
	public @NotNull Class<Sounds> capability() {
		return Sounds.class;
	}

	@Override
	public @NotNull Sounds create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelSounds(context.channel());
	}
}
