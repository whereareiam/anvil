package me.whereareiam.anvil.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import me.whereareiam.anvil.api.type.network.NetworkServerAccess;
import org.jetbrains.annotations.NotNull;

/**
 * Declares topology and listener exposure requirements for a scenario.
 * Strict process isolation is enforced only by execution providers that advertise it.
 */
@Value
@Builder(toBuilder = true)
public class NetworkPolicy {
	/**
	 * Host address used when exposing process listeners. Loopback is the default.
	 */
	@NotNull
	@Builder.Default
	String bindAddress = "127.0.0.1";

	/**
	 * Explicit permission for non-loopback listeners; the scenario must also be manual.
	 */
	@Builder.Default
	boolean allowLanBinding = false;

	/**
	 * Which scenario processes may reach backend server listeners.
	 */
	@NotNull
	@Builder.Default
	NetworkServerAccess networkServerAccess = NetworkServerAccess.ANY_PROCESS;

	/**
	 * Host exposure requested for proxy listeners.
	 */
	@NotNull
	@Builder.Default
	NetworkExposure proxyNetworkExposure = NetworkExposure.LOOPBACK;

	/**
	 * Host exposure requested for backend listeners.
	 */
	@NotNull
	@Builder.Default
	NetworkExposure backendNetworkExposure = NetworkExposure.LOOPBACK;
}
