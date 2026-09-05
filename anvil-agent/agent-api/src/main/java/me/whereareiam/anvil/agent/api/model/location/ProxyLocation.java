package me.whereareiam.anvil.agent.api.model.location;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Location observed by a proxy agent.
 */
@Value
@Builder
public class ProxyLocation implements AgentLocation {
    /**
     * Proxy identifier.
     */
    @NotNull String proxy;
    /**
     * Current backend server, when the proxy has one.
     */
    @Nullable String connectedServer;
}
