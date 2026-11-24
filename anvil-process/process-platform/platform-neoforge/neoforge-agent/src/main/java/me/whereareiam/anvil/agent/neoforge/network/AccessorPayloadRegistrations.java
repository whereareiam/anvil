package me.whereareiam.anvil.agent.neoforge.network;

import me.whereareiam.anvil.agent.neoforge.api.PayloadRegistrations;
import me.whereareiam.anvil.agent.neoforge.mixin.NetworkRegistryAccessor;
import net.minecraft.network.ConnectionProtocol;
import net.neoforged.neoforge.network.registration.PayloadRegistration;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The payload channels NeoForge registered, read through the agent's accessor of NeoForge's network registry.
 */
public final class AccessorPayloadRegistrations implements PayloadRegistrations {
	@Override
	public @NotNull Map<ConnectionProtocol, Map<?, PayloadRegistration<?>>> all() {
		return NetworkRegistryAccessor.anvil$registrations();
	}
}
