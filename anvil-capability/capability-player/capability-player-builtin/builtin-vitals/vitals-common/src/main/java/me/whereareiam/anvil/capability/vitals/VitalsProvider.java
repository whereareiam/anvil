package me.whereareiam.anvil.capability.vitals;

import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import me.whereareiam.anvil.capability.vitals.channel.VitalsChannel;
import me.whereareiam.anvil.protocol.java.api.JavaProtocol;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * Host-side provider for the player's health, food and deaths, over the player's typed channel, for the libraries the
 * vitals family has a worker side for. Composition creates it only for players whose worker installed the vitals
 * extension.
 */
public final class VitalsProvider implements ProtocolPlayerCapabilityProvider<Vitals> {
	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return CapabilityDescriptor.builder()
				.id(VitalsChannel.ID)
				.build();
	}

	@Override
	public @NotNull Set<Class<?>> requiredProtocols() {
		return Set.of(JavaProtocol.class);
	}

	@Override
	public @NotNull Class<Vitals> capability() {
		return Vitals.class;
	}

	@Override
	public @NotNull Vitals create(@NotNull ProtocolPlayerCapabilityContext context) {
		return new ChannelVitals(context.channel());
	}
}
