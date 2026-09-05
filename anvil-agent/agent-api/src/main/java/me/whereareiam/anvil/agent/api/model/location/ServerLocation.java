package me.whereareiam.anvil.agent.api.model.location;

import lombok.Builder;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Location observed by a Minecraft server agent.
 */
@Value
@Builder
public class ServerLocation implements AgentLocation {
    /**
     * Server name.
     */
    @NotNull String server;
}
