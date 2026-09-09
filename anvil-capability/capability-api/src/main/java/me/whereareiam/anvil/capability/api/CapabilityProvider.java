package me.whereareiam.anvil.capability.api;

import me.whereareiam.anvil.api.capability.Capability;
import me.whereareiam.anvil.capability.api.model.CapabilityDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * Contributes one typed capability to an owner through an owner-specific context.
 *
 * @param <C> contributed capability type
 * @param <X> owner-specific creation context
 */
public interface CapabilityProvider<C extends Capability, X> {
	/**
	 * Describes the provider identity, contract version, and capability dependencies.
	 *
	 * @return immutable provider descriptor
	 */
	@NotNull CapabilityDescriptor descriptor();

	/**
	 * Returns the exact public capability type contributed by this provider.
	 *
	 * @return capability class or interface
	 */
	@NotNull Class<C> capability();

	/**
	 * Creates one capability for the supplied owner. Register acquired resources with the
	 * context before performing later work that can fail, so partial creation can be released.
	 *
	 * @param context owner-specific services and declared dependencies
	 * @return capability implementation
	 */
	@NotNull C create(@NotNull X context);
}
