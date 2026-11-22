package me.whereareiam.anvil.agent.neoforge.api;

import net.minecraft.network.ConnectionProtocol;
import net.neoforged.neoforge.network.registration.PayloadRegistration;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * The payload channels NeoForge registered, which the NeoForge agent offers as a service to the NeoForge sides of
 * capabilities: {@code platform.requireService(PayloadRegistrations.class)}. NeoForge keeps them to itself and offers no
 * way to list them, so the agent reads them through an accessor of its own.
 */
public interface PayloadRegistrations {
	/**
	 * Returns the registered channels by connection phase and id. The ids are resource locations until Minecraft
	 * 1.21.11 and identifiers from then on, so the map leaves their type open; each registration also names its id.
	 *
	 * @return the registrations, as NeoForge keeps them
	 */
	@NotNull Map<ConnectionProtocol, Map<?, PayloadRegistration<?>>> all();
}
