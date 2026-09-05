package me.whereareiam.anvil.capability.api;

import me.whereareiam.anvil.api.player.PlayerCapability;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.ServiceLoader;

/**
 * Service-provider contract that contributes one typed capability to every compatible player.
 *
 * <p>Implementations are discovered through {@link ServiceLoader}. A provider JAR must
 * list its implementation under
 * {@code META-INF/services/me.whereareiam.anvil.capability.api.PlayerCapabilityProvider}.</p>
 *
 * @param <C> contributed capability type
 */
public interface PlayerCapabilityProvider<C extends PlayerCapability> {
	/**
	 * Describes the provider and its capability dependencies.
	 *
	 * @return immutable provider descriptor
	 */
	@NotNull CapabilityDescriptor descriptor();

	/**
	 * Returns the unique capability type contributed by this provider.
	 *
	 * @return capability interface
	 */
	@NotNull Class<C> capability();

	/**
	 * Creates the capability for one simulated player.
	 *
	 * @param context player-scoped provider context
	 * @return capability implementation
	 */
	@NotNull C create(@NotNull PlayerCapabilityContext context);
}
