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
 * <p>A provider names what it needs from the player's library instead of naming libraries: the edition protocols
 * its worker extension speaks through {@link #requiredProtocols()}, and through {@link #workerBacked()} whether a
 * worker extension serves it at all. For the players of a library that does not speak a required protocol, or that
 * runs no worker extensions while the provider is worker-backed, the provider and every provider depending on its
 * capability are skipped, and requesting the capability reports why. For a player with a native worker, a
 * worker-backed provider whose capability the worker does not install is skipped the same way with the worker's
 * reason, so the provider must not check the worker's installed capabilities itself.</p>
 *
 * <pre>{@code
 * public final class TeleportProvider implements ProtocolPlayerCapabilityProvider<Teleport> {
 *     public Set<Class<?>> requiredProtocols() { return Set.of(JavaProtocol.class); }
 *     public boolean workerBacked() { return true; }
 *     ...
 * }
 * }</pre>
 *
 * @param <C> contributed capability type
 */
public interface ProtocolPlayerCapabilityProvider<C extends PlayerCapability>
		extends CapabilityProvider<C, ProtocolPlayerCapabilityContext> {
	/**
	 * Limits discovery to compatible protocol libraries, for a capability that works only through one library's own
	 * services. A capability served by a worker extension declares its {@link #requiredProtocols() protocols}
	 * instead. An empty set places no restriction on the player's library.
	 *
	 * @return supported protocol-library identifiers, or an empty set
	 */
	default @NotNull Set<String> supportedLibraries() {
		return Set.of();
	}

	/**
	 * Returns the edition protocols the capability's worker extension speaks, such as {@code JavaProtocol}. The
	 * capability is composed only for players whose library speaks every one of them.
	 *
	 * @return required protocol interfaces, or an empty set when the capability needs none
	 */
	default @NotNull Set<Class<?>> requiredProtocols() {
		return Set.of();
	}

	/**
	 * Tells whether a worker extension with this provider's capability ID serves the capability through the player's
	 * channel. Such a capability is skipped for players without a native worker.
	 *
	 * @return whether the capability needs the player's worker
	 */
	default boolean workerBacked() {
		return !requiredProtocols().isEmpty();
	}
}
