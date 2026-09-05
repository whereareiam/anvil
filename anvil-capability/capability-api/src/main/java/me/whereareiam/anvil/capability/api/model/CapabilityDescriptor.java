package me.whereareiam.anvil.capability.api.model;

import me.whereareiam.anvil.api.player.PlayerCapability;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

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
	 * Provider contract version understood by this Anvil release.
	 */
	@Builder.Default int apiVersion = CURRENT_API_VERSION;
	/**
	 * Capability types that must be created before this provider.
	 */
	@NotNull @Singular Set<Class<? extends PlayerCapability>> requiredCapabilities;
	/**
	 * Protocol-provider IDs supported by this implementation. An empty set means backend-neutral.
	 */
	@NotNull @Singular("supportedProtocolId") Set<String> supportedProtocolIds;
}
