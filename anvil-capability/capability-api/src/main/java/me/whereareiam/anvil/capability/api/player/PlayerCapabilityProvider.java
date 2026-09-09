package me.whereareiam.anvil.capability.api.player;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.CapabilityProvider;

import java.util.ServiceLoader;

/**
 * Contributes player-owned behavior using identity, observations, and declared capability dependencies.
 * The factory does not select a protocol or receive transport access. Mechanism-specific factories
 * supply their own scoped creation contracts while sharing the same player capability lifetime.
 *
 * <p>Implementations are discovered through {@link ServiceLoader}. A provider JAR registers its
 * implementation under
 * {@code META-INF/services/me.whereareiam.anvil.capability.api.player.PlayerCapabilityProvider}.</p>
 *
 * @param <C> contributed player capability type
 */
public interface PlayerCapabilityProvider<C extends PlayerCapability>
		extends CapabilityProvider<C, PlayerCapabilityContext> { }
