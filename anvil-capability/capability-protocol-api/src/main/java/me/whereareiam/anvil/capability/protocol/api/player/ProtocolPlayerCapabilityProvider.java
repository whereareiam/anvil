package me.whereareiam.anvil.capability.protocol.api.player;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ServiceLoader;
import java.util.Set;

/**
 * Contributes player-owned behavior using the native channel or external services of the protocol
 * library selected for each player.
 *
 * <p>Implementations are discovered through {@link ServiceLoader}. A provider JAR must
 * list its implementation under
 * {@code META-INF/services/me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider}.</p>
 *
 * <p>When a player has a native worker that does not install this provider's capability ID, the provider
 * and every provider depending on its capability are skipped for that player. Requesting the capability
 * then reports the worker's reason, or that the library's worker does not install it. A player without a
 * native worker, whose library works only through its own services, composes the provider as usual, so
 * the provider must not check the worker's installed capabilities itself.</p>
 *
 * @param <C> contributed capability type
 */
public interface ProtocolPlayerCapabilityProvider<C extends PlayerCapability>
		extends CapabilityProvider<C, ProtocolPlayerCapabilityContext> {
	/**
	 * Limits discovery to compatible protocol libraries. An empty set places no restriction on
	 * the player's library; the factory still requires the library's services to be available.
	 *
	 * @return supported protocol-library identifiers, or an empty set
	 */
	default @NotNull Set<String> supportedLibraries() {
		return Set.of();
	}
}
