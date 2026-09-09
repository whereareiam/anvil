package me.whereareiam.anvil.capability.protocol.api.player;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.CapabilityProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ServiceLoader;
import java.util.Set;

/**
 * Contributes player-owned behavior using the selected protocol backend's native channel or SDK services.
 *
 * <p>Implementations are discovered through {@link ServiceLoader}. A provider JAR must
 * list its implementation under
 * {@code META-INF/services/me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityProvider}.</p>
 *
 * @param <C> contributed capability type
 */
public interface ProtocolPlayerCapabilityProvider<C extends PlayerCapability>
		extends CapabilityProvider<C, ProtocolPlayerCapabilityContext> {
	/**
	 * Limits discovery to compatible protocol providers. An empty set places no restriction on
	 * the selected provider ID; the factory still requires its backend services to be available.
	 *
	 * @return supported protocol-provider identifiers, or an empty set
	 */
	default @NotNull Set<String> supportedProtocolIds() {
		return Set.of();
	}
}
