package me.whereareiam.anvil.platform.api.model;

import lombok.Builder;
import lombok.ToString;
import lombok.Value;
import me.whereareiam.anvil.platform.api.type.ForwardingMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Identity-forwarding configuration negotiated for connected servers and proxies before launch.
 * Providers consume this configuration without inspecting another provider's platform ID.
 */
@Value
@Builder
public class ForwardingConfiguration {
	/**
	 * Selected forwarding protocol; direct servers use {@link ForwardingMode#NONE}.
	 */
	@NotNull
	@Builder.Default
	ForwardingMode mode = ForwardingMode.NONE;
	/**
	 * Whether the connected proxies authenticate players online.
	 */
	boolean proxyOnlineMode;
	/**
	 * Per-run forwarding secret, absent for direct connections. Modern forwarding authenticates
	 * with it; a proxy may also require a secret file when configured for legacy forwarding.
	 */
	@Nullable
	@ToString.Exclude
	String secret;
}
