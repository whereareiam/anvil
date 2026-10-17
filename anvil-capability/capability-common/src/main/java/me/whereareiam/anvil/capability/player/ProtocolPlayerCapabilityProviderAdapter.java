package me.whereareiam.anvil.capability.player;

import lombok.RequiredArgsConstructor;
import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityContext;
import me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext;
import me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Includes protocol factories in the shared player graph while requiring their specialized inputs.
 */
@RequiredArgsConstructor
final class ProtocolPlayerCapabilityProviderAdapter<C extends PlayerCapability> implements PlayerCapabilityProvider<C> {
	private final @NotNull ProtocolPlayerCapabilityProvider<C> provider;

	@Override
	public @NotNull CapabilityDescriptor descriptor() {
		return provider.descriptor();
	}

	@Override
	public @NotNull Class<C> capability() {
		return provider.capability();
	}

	@Override
	public @NotNull C create(@NotNull PlayerCapabilityContext context) {
		if (!(context instanceof ProtocolPlayerCapabilityContext protocol))
			throw new CapabilityException("Capability '" + descriptor().getId() + "' requires protocol player inputs");

		return provider.create(protocol);
	}
}
