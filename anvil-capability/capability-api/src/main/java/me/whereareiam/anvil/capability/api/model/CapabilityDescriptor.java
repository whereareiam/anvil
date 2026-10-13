package me.whereareiam.anvil.capability.api.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.anvil.api.capability.Capability;
import me.whereareiam.anvil.api.model.PresentationMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Immutable identity and dependency declaration for one capability provider.
 */
@Value
@Builder
public class CapabilityDescriptor {
	/**
	 * Current capability contract version understood by this Anvil release.
	 */
	public static final int CURRENT_API_VERSION = 1;

	/**
	 * Stable provider identifier used for discovery diagnostics.
	 */
	@NotNull String id;
	/**
	 * Optional display details that do not change dependency or provider identity.
	 */
	@Nullable PresentationMetadata metadata;
	/**
	 * Provider contract version understood by this Anvil release.
	 */
	@Builder.Default int apiVersion = CURRENT_API_VERSION;
	/**
	 * Capability types that must be created before this provider. Without one of them, the provider is not created
	 * and its capability reports the missing one as the reason.
	 */
	@NotNull @Singular Set<Class<? extends Capability>> requiredCapabilities;
	/**
	 * Capability types this provider uses when the owner has them. Those the owner has are created before this
	 * provider and can be retrieved from its context; one the owner lacks or cannot create is absent from the context
	 * and never keeps the provider from being created.
	 */
	@NotNull @Singular Set<Class<? extends Capability>> optionalCapabilities;
}
