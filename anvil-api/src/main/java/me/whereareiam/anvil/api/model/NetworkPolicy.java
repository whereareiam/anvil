package me.whereareiam.anvil.api.model;

import lombok.Builder;
import lombok.Value;
import me.whereareiam.anvil.api.type.network.NetworkServerAccess;
import me.whereareiam.anvil.api.type.network.NetworkExposure;
import org.jetbrains.annotations.NotNull;

/**
 * Declares topology and listener exposure requirements for a scenario.
 * Strict process isolation is enforced only by execution providers that advertise it.
 */
@Value
@Builder
public class NetworkPolicy {
    @NotNull
    @Builder.Default
    NetworkServerAccess networkServerAccess = NetworkServerAccess.ANY_PROCESS;
    @NotNull
    @Builder.Default
    NetworkExposure proxyNetworkExposure = NetworkExposure.LOOPBACK;
    @NotNull
    @Builder.Default
    NetworkExposure backendNetworkExposure = NetworkExposure.LOOPBACK;
}
